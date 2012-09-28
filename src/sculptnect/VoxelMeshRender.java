package sculptnect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import toxi.geom.Vec3D;
import toxi.geom.mesh.TriangleMesh;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.vecmath.Point3i;
import javax.vecmath.Tuple3i;

import com.jogamp.common.nio.Buffers;

/** Tessellates a voxel grid into a naive surfacenet mesh, most suitable for
 *  interactive deformations and real-time rendering. Naive because displacement
 *  of the surfacenet verts inside their respective voronoi cells is very quick
 *  and rudimentary.
 *  
 *  Play around with num_threads and chunk_size for optimal performance.
 *  Should manage around 256^3 voxel grids on a standard notebook (2012).
 */
public class VoxelMeshRender {
	private static final int NUM_THREADS = 8;
	private static final int CHUNK_SIZE = 32;// WARN: make sure power of two otherwise markVoxelDirty method fails
	private static final int VERTEX_SIZE = 3;
	private static final int NORMAL_SIZE = 3;
	private static final int ITEM_SIZE = VERTEX_SIZE + NORMAL_SIZE;
	private static final int NORMAL_STRIDE = Buffers.SIZEOF_FLOAT * NORMAL_SIZE;
	private static final int ITEM_STRIDE = Buffers.SIZEOF_FLOAT * ITEM_SIZE;
	
	private static final int VERTEX_GRID_OFFSET = 1;// offset of surfacenet cells in grid +2 on each side because chunk updates propagate 2 vertices wider than the actual chunk for better normal calculations
	private static final float VERTEX_OFFSET = 0.5f;// offset of surfacenet node
	
	ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

	ReentrantLock dirtyMarkingLock = new ReentrantLock();
	
	BlockingSet<Chunk> waitingChunkSet = new BlockingSet<VoxelMeshRender.Chunk>();
	BlockingSet<Chunk> completedChunkSet = new BlockingSet<VoxelMeshRender.Chunk>();
	
	BlockingQueue<ChunkData> chunkDataQueue = new ArrayBlockingQueue<ChunkData>(NUM_THREADS, false);
	
	// collection of all verts + normals created inside every voronoi cell
	//float[][][][] nodes;

	VoxelGrid grid;
	Chunk[][][] chunks;
	Set<Chunk> dirtyChunks = Collections.synchronizedSet(new HashSet<Chunk>());
	Set<Chunk> visibleChunks = new HashSet<Chunk>();
	
	int[] dimensions = new int[3];
	
	/** Keeps track of a subarea of the voxel grid and also of the vertices/nodes
	 *  which embodies the surfacenet.
	 */
	private class Chunk {
		Tuple3i position = new Point3i();
		int[] lowerIndices = new int[3];// The lower indices of the voxel grid for this chunk
		int[] upperIndices = new int[3];// The upper indices of the voxel grid for this chunk
		int bufferName;// The buffer object name used as handle in OpenGL
		int numIndices;// The number of indices this buffer object contains
		int numNewIndices;
		boolean dirty;
		ChunkData chunkData;
		
		public void init (ChunkData chunkData) {
			//this.chunkData = chunkData;
			chunkData.vertexBuffer.clear();
			chunkData.faces.clear();
			numNewIndices = 0;
			
			// build/reset surfacenet nodes
			// the nodes actually ends up displaced +0.5 but because of 'nodes' offset we add -0.5
			float[][][][] nodes = chunkData.nodes;
			for (int x=0; x<CHUNK_SIZE + VERTEX_GRID_OFFSET*2; ++x)
				for (int y=0; y<CHUNK_SIZE + VERTEX_GRID_OFFSET*2; ++y)
					for (int z=0; z<CHUNK_SIZE + VERTEX_GRID_OFFSET*2; ++z) {
						float[] node = nodes[x][y][z];
						node[0] = lowerIndices[0] + x - VERTEX_GRID_OFFSET + VERTEX_OFFSET;
						node[1] = lowerIndices[1] + y - VERTEX_GRID_OFFSET + VERTEX_OFFSET;
						node[2] = lowerIndices[2] + z - VERTEX_GRID_OFFSET + VERTEX_OFFSET;
						node[3] = 0;
						node[4] = 0;
						node[5] = 0;
					}
		}
	}
	
	/** Aggregation of data structures, one for every thread, storing data when
	 *  processing a chunk.
	 */
	private class ChunkData {
		float[][][][] nodes = new float
				[CHUNK_SIZE + VERTEX_GRID_OFFSET*2][CHUNK_SIZE + VERTEX_GRID_OFFSET*2]
				[CHUNK_SIZE + VERTEX_GRID_OFFSET*2][ITEM_SIZE];// collection of all verts + normals created inside every voronoi cell
		FloatBuffer vertexBuffer = ByteBuffer
			.allocateDirect(
				(CHUNK_SIZE + VERTEX_GRID_OFFSET*2) *
				(CHUNK_SIZE + VERTEX_GRID_OFFSET*2) *
				(CHUNK_SIZE + VERTEX_GRID_OFFSET*2) * ITEM_STRIDE * 3)// from every vert 6 tris can originate but wont in practise because of geometric limitations, so should be much more
			.order(ByteOrder.nativeOrder()).asFloatBuffer();
		ArrayList<float[]> faces = new ArrayList<float[]>();// contains all triangles
	}
	
	/** Threaded and responsible for creating and updating all vertices
	 *  belonging to the chunks picked from queue.
	 */
	private class ChunkVertexCreator implements Runnable {
		// Accumulate all vertices/nodes for a chunk in 'faces'. This way the
		// verts can be connected and reused and displaced within its voronoi
		// cells after they've been added to the list.
		//
		// Every other element is a vert and a normal.
		// 3 verts + 3 normals == 1 tri (counter-clockwise)
		// Together, each set of six elements forms a set of faces.

		VoxelGrid grid;
		
		public ChunkVertexCreator (VoxelGrid grid) {
			this.grid = grid;
		}
		
		@Override
		public void run() {
			try {
				while (true) {
					Chunk chunk = waitingChunkSet.take();
					ChunkData chunkData = chunkDataQueue.take();
					
					chunk.init(chunkData);
					FloatBuffer vertexBuffer = chunkData.vertexBuffer;
					ArrayList<float[]> faces = chunkData.faces;
					
					// displace nodes, build faces
					for (int x=chunk.lowerIndices[0]; x<chunk.upperIndices[0]; ++x)
						for (int y=chunk.lowerIndices[1]; y<chunk.upperIndices[1]; ++y)
							for (int z=chunk.lowerIndices[2]; z<chunk.upperIndices[2]; ++z)
								updateVertices(chunk, chunkData, x, y, z);
					
					chunk.numNewIndices = chunkData.faces.size();
					
					// draw faces
					for (float[] vertex : faces)
						vertexBuffer.put(vertex);
					
					vertexBuffer.rewind();
					chunk.chunkData = chunkData;
					completedChunkSet.add(chunk);// send off chunk for rendering
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		private void updateVertices (Chunk chunk, ChunkData chunkData, int x, int y, int z) {
			/*
			 * Find all relevant edge crossings, ie where a cell transitions from
			 * matter->air or other way around. An edge crossing means we have two
			 * adjacent cells with which we can create a tri with its normal
			 * pointing in the direction of the air voxel.
			 * 
			 * Build tris in either strictly positive directions or strictly
			 * negative. Otherwise adjacent cells will build intersecting tris.
			 * This means we build in all six orthogonal directions with only six
			 * different tris facing one of two possible directions - thats a total
			 * of twelve different tris.
			 * 
			 * This means we only have to check half the edges, those originating
			 * from either c0 (left, down, back) or c7 (right, up, front).
			 * 
			 * 
			 * Interpolating the current cell vertex position:
			 * 
			 * - Either do this rudimentary by perhaps just summing up all the
			 * edges and dividing the resulting vector by the amount of summations
			 * and multiplying by a factor.
			 * 
			 * - Or we can go more along the lines of the original
			 * surfacenet definition and find position inside the bounds
			 * of the cell (voronoi cell) and try to minimize the total
			 * distance to adjacent vertices. Frisken refers to this
			 * process as relaxation, lowering the energy in the
			 * surfacenet. This requires a lookup of all six possibly
			 * adjacent vertices, and also multiple iterations.
			 * 
			 * The latter method isnt really real-time feasible.
			 */
			
			// get all corners
			boolean c0 = ! grid.isAir(x, y, z);// left down back
			boolean c1 = ! grid.isAir(x+1, y, z);// right down back
			boolean c2 = ! grid.isAir(x, y+1, z);// left up back
			boolean c3 = ! grid.isAir(x, y, z+1);// left down up
			boolean c4 = ! grid.isAir(x+1, y+1, z);// right up back
			boolean c5 = ! grid.isAir(x+1, y, z+1);// right down front
			boolean c6 = ! grid.isAir(x, y+1, z+1);// left up front
			boolean c7 = ! grid.isAir(x+1, y+1, z+1);// right up front
			
			// If no relevant edge crossings exist (we mustnt check all)
			if (c0==c1 && c1==c2 && c2==c3 && c7==c4 && c7==c5 && c7==c6)
				return;

			ArrayList<float[]> faces = chunkData.faces;
			float[][][][] nodes = chunkData.nodes;
			
			// get node/vertex local coords in chunk, compensate for offset
			int vx = x - chunk.lowerIndices[0] + VERTEX_GRID_OFFSET;
			int vy = y - chunk.lowerIndices[1] + VERTEX_GRID_OFFSET;
			int vz = z - chunk.lowerIndices[2] + VERTEX_GRID_OFFSET;
			
			// get current vertex/node
			float[] v = nodes[vx][vy][vz];
			
			// adjust vertex local position within the cell
			displace(x, y, z, v);
			
			if (c0!=c1 || c0!=c2 || c0!=c3) {
				// get adjacent vertices
				float[] vx0 = nodes[vx-1][vy][vz];// left
				float[] vy0 = nodes[vx][vy-1][vz];// down
				float[] vz0 = nodes[vx][vy][vz-1];// back
				
				// adjust border vertices in case an adjacent chunk have changed, otherwise tearing and normal tearing occurs at chunk borders
				if (x == chunk.lowerIndices[0]) displace(x-1, y, z, vx0);
				if (y == chunk.lowerIndices[1]) displace(x, y-1, z, vy0);
				if (z == chunk.lowerIndices[2]) displace(x, y, z-1, vz0);
				
				// generate triangles if the three bottom edges are crossed
				if (c0) {
					if (!c1) addFace(faces, v, vy0, vz0);// down back
					if (!c2) addFace(faces, v, vz0, vx0);// back left
					if (!c3) addFace(faces, v, vx0, vy0);// left down
				}
				else {
					if (c1) addFace(faces, v, vz0, vy0);// back down
					if (c2) addFace(faces, v, vx0, vz0);// left back
					if (c3) addFace(faces, v, vy0, vx0);// down left
				}
			}
			
			if (c7!=c4 || c7!=c5 || c7!=c6) {
				// get current vertex and adjacent vertices
				float[] vx1 = nodes[vx+1][vy][vz];// right
				float[] vy1 = nodes[vx][vy+1][vz];// up
				float[] vz1 = nodes[vx][vy][vz+1];// front
				
				// adjust border vertices in case an adjacent chunk have changed, otherwise normal tearing occurs at chunk borders
				if (x == chunk.upperIndices[0]-1) displace(x+1, y, z, vx1);
				if (y == chunk.upperIndices[1]-1) displace(x, y+1, z, vy1);
				if (z == chunk.upperIndices[2]-1) displace(x, y, z+1, vz1);
				
				// generate triangles if the three top edges are crossed
				if (c7) {
					if (!c4) addFace(faces, v, vy1, vx1);// y+1, x+1
					if (!c5) addFace(faces, v, vx1, vz1);// x+1, z+1
					if (!c6) addFace(faces, v, vz1, vy1);// z+1, y+1
				}
				else {
					if (c4) addFace(faces, v, vx1, vy1);// x+1, y+1
					if (c5) addFace(faces, v, vz1, vx1);// z+1, x+1
					if (c6) addFace(faces, v, vy1, vz1);// y+1, z+1
				}
			}
			
		}
		
		private void addFace (ArrayList<float[]> faces, float[] v0, float[] v1, float[] v2) {
			faces.add(v0);
			faces.add(v1);
			faces.add(v2);
		}
		
		/** THE smoothing algorithm for displacing the vert within the voronoi cell
		 *  v is a float[6] node containing a vert and a normal
		 */
		private void displace (int x, int y, int z, float[] v) {
			boolean c0 = ! grid.isAir(x, y, z);// left down back
			boolean c1 = ! grid.isAir(x+1, y, z);// right down back
			boolean c2 = ! grid.isAir(x, y+1, z);// left up back
			boolean c3 = ! grid.isAir(x, y, z+1);// left down up
			boolean c4 = ! grid.isAir(x+1, y+1, z);// right up back
			boolean c5 = ! grid.isAir(x+1, y, z+1);// right down front
			boolean c6 = ! grid.isAir(x, y+1, z+1);// left up front
			boolean c7 = ! grid.isAir(x+1, y+1, z+1);// right up front
			
			float dx = 0, dy = 0, dz = 0;
			
			// do local displacement
			// displace in direction of every cube side with at least one zero crossing
			final float m = 0.3f;// magnitude
			if (c0 != c2 || c0 != c3 || c0 != c6) dx -= m;// left
			if (c0 != c1 || c0 != c3 || c0 != c5) dy -= m;// down
			if (c0 != c1 || c0 != c2 || c0 != c4) dz -= m;// back
			if (c7 != c1 || c7 != c4 || c7 != c5) dx += m;// right
			if (c7 != c2 || c7 != c4 || c7 != c6) dy += m;// up
			if (c7 != c3 || c7 != c5 || c7 != c6) dz += m;// front
			
			// clear any previous displacement and set current
			v[0] = x + dx + VERTEX_OFFSET;
			v[1] = y + dy + VERTEX_OFFSET;
			v[2] = z + dz + VERTEX_OFFSET;
			setNormal(x,y,z,v);
		}
		
		private void setNormal (int x, int y, int z, float[] v) {
			// Iterate through the 125 adjacent voxels
			final int min = -2;// -2
			final int max = 3;// 3
			for (int xd = min; xd < max; ++xd)
				for (int yd = min; yd < max; ++yd)
					for (int zd = min; zd < max; ++zd) {
						int xoff = x + xd;
						int yoff = y + yd;
						int zoff = z + zd;
						
						boolean isInside = xoff >= 0 && xoff < grid.width && yoff >= 0 && yoff < grid.height && zoff >= 0 && zoff < grid.depth;
						
						if (isInside && grid.isAir(xoff, yoff, zoff)) {
							// If the neighbor voxel is empty, add the
							// direction
							// to it to the normal
							v[3] += xd;
							v[4] += yd;
							v[5] += zd;
						}
					}
			
			float len = (float) Math.sqrt(v[3] * v[3] + v[4] * v[4] + v[5] * v[5]);
			v[3] /= len;
			v[4] /= len;
			v[5] /= len;
		}
	}
	
	public VoxelMeshRender (VoxelGrid grid) {
		this.grid = grid;
		
		// Add floatbuffers to queue, and add Chunk creators to executor
		for (int i=0; i<NUM_THREADS; ++i) {
			chunkDataQueue.offer(new ChunkData());
			executor.submit(new ChunkVertexCreator(grid));
		}
		
		// Calculate buffer dimensions
		dimensions[0] = (int) Math.ceil((double) grid.width / CHUNK_SIZE);
		dimensions[1] = (int) Math.ceil((double) grid.height / CHUNK_SIZE);
		dimensions[2] = (int) Math.ceil((double) grid.depth / CHUNK_SIZE);

		// Create the buffer chunks and initialize them
		chunks = new Chunk[dimensions[0]][dimensions[1]][dimensions[2]];
		
		for (int x=0; x<dimensions[0]; ++x)
			for (int y=0; y<dimensions[1]; ++y)
				for (int z=0; z<dimensions[2]; ++z) {
					Chunk chunk = new Chunk();
					chunks[x][y][z] = chunk;
					
					chunk.position.set(x, y, z);
					
					// set chunk position and indices bounds
					chunk.lowerIndices[0] = Math.max(1, x * CHUNK_SIZE);
					chunk.lowerIndices[1] = Math.max(1, y * CHUNK_SIZE);
					chunk.lowerIndices[2] = Math.max(1, z * CHUNK_SIZE);
					
					// kMax-1 because a cell is always in voxel interval [k,k+1]
					chunk.upperIndices[0] = Math.min((x + 1) * CHUNK_SIZE, grid.width - 1);
					chunk.upperIndices[1] = Math.min((y + 1) * CHUNK_SIZE, grid.height - 1);
					chunk.upperIndices[2] = Math.min((z + 1) * CHUNK_SIZE, grid.depth - 1);
				}
	}
	
	public void refresh () {
		beginVoxelMarking();
		
		for (int x = 0; x < dimensions[0]; ++x)
			for (int y = 0; y < dimensions[1]; ++y)
				for (int z = 0; z < dimensions[2]; ++z)
					dirtyChunks.add(chunks[x][y][z]);
		
		endVoxelMarking();
	}
	
	public void beginVoxelMarking() {
		dirtyMarkingLock.lock();
	}

	public void endVoxelMarking() {
		waitingChunkSet.addAll(dirtyChunks);

		for (Chunk chunk : dirtyChunks)
			chunk.dirty = false;
		
		dirtyChunks.clear();
		dirtyMarkingLock.unlock();
	}
	
	public void markVoxelDirty (int x, int y, int z) {
		final int d = (int) (Math.log(CHUNK_SIZE) / Math.log(2));
//		final int m = CHUNK_SIZE - 1;
		
		// chunk index
		int ix = x >> d;// quicker division if d is power of 2
		int iy = y >> d;
		int iz = z >> d;
		
		Chunk chunk = chunks[ix][iy][iz];
		
		if (!chunk.dirty) {
			chunk.dirty = true;
			dirtyChunks.add(chunk);
		}
							
		// if x % CHUNK_SIZE == 0 or 1 adjacent chunks must be marked dirty to prevent mesh tearings 
//		int mX = x & m;// quicker modulo if m is power of 2 minus 1
//		int mY = y & m;
//		int mZ = z & m;
//		
//		if (mX == 0 && x > 0) dirtyChunks.add(chunks[ix-1][iy][iz]);
//		else if (mX == 1 && x<grid.w-CHUNK_SIZE) dirtyChunks.add(chunks[ix+1][iy][iz]);
//		
//		if (mY == 0 && y > 0) dirtyChunks.add(chunks[ix][iy-1][iz]);
//		else if (mY == 1 && y<grid.h-CHUNK_SIZE) dirtyChunks.add(chunks[ix][iy+1][iz]);
//		
//		if (mZ == 0 && z > 0) dirtyChunks.add(chunks[ix][iy][iz-1]);
//		else if (mZ == 1 && z<grid.d-CHUNK_SIZE) dirtyChunks.add(chunks[ix][iy][iz+1]);
	}

	public void updateDirtyChunks (GL2 gl) {
		long startTime = System.nanoTime();
		final long frameTime = (long) (1000000000 * (1.0f / 100.0f));

		try {
			while (true) {
				long timeLeft = frameTime - (System.nanoTime() - startTime);
				if (timeLeft <= 0)
					break;

				Chunk chunk = completedChunkSet.poll(timeLeft);
				if (chunk == null)
					break;

				// If this cell doesn't contain any points, remove it from the
				// visible buffer cell set
				if (chunk.numNewIndices == 0) {
					visibleChunks.remove(chunk);
				} else {
					visibleChunks.add(chunk);
				}
				
				ChunkData chunkData = chunk.chunkData;
				chunk.chunkData = null;
				
				if (chunk.bufferName == 0) {
					// Generate and set a buffer object name for this cell
					int[] buf = new int[1];
					gl.glGenBuffers(1, buf, 0);
					chunk.bufferName = buf[0];
				}

				// Upload the vertex and normal data to the buffer
				gl.glBindBuffer(GL.GL_ARRAY_BUFFER, chunk.bufferName);
				gl.glBufferData(GL.GL_ARRAY_BUFFER, chunk.numNewIndices * ITEM_STRIDE, chunkData.vertexBuffer, GL.GL_STATIC_DRAW);
				gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);

				chunk.numIndices = chunk.numNewIndices;
				chunkDataQueue.offer(chunkData);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public void draw (GL2 gl) {
		gl.glColor3f(.3f, .7f, .7f);
		
		// Enable the vertex and normal arrays
		gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
		gl.glEnableClientState(GL2.GL_NORMAL_ARRAY);
		
		// Loop through visible chunks and draw them
		for (Chunk chunk : visibleChunks) {
			gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, chunk.bufferName);// Bind buffer containing vertices and normals
			gl.glVertexPointer(3, GL2.GL_FLOAT, ITEM_STRIDE, 0);// Specify vertex data
			gl.glNormalPointer(GL2.GL_FLOAT, ITEM_STRIDE, ITEM_STRIDE - NORMAL_STRIDE);
			gl.glDrawArrays(GL2.GL_TRIANGLES, 0, chunk.numIndices);// Draw the points
		}

		gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);// Unbind the buffer data
		
		// Disable vertex and normal arrays
		gl.glDisableClientState(GL2.GL_NORMAL_ARRAY);
		gl.glDisableClientState(GL2.GL_VERTEX_ARRAY);
	}
	
	public synchronized void dump (GL2 gl) {
		FloatBuffer vertexBuffer = ByteBuffer
				.allocateDirect(
					(CHUNK_SIZE + VERTEX_GRID_OFFSET*2) *
					(CHUNK_SIZE + VERTEX_GRID_OFFSET*2) *
					(CHUNK_SIZE + VERTEX_GRID_OFFSET*2) * ITEM_STRIDE * 3)// from every vert 6 tris can originate but wont in practise because of geometric limitations, so should be much more
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		
		Vec3D v0 = new Vec3D();
		Vec3D v1 = new Vec3D();
		Vec3D v2 = new Vec3D();
		
		// dumping all crashes so dump individual chunks
		
		for (int x=0; x<dimensions[0]; ++x)
			for (int y=0; y<dimensions[1]; ++y)
				for (int z=0; z<dimensions[2]; ++z) {
					vertexBuffer.clear();
					Chunk chunk = chunks[x][y][z];
					TriangleMesh mesh = new TriangleMesh("sculptnect");
					
					gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, chunk.bufferName);
					gl.glGetBufferSubData(GL2.GL_ARRAY_BUFFER, 0, chunk.numIndices * ITEM_STRIDE, vertexBuffer);
					gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);// Unbind the buffer data
					for (int j=0; j<chunk.numIndices; j+=3) {
						v0.set(vertexBuffer.get(), vertexBuffer.get(), vertexBuffer.get());
						vertexBuffer.get(); vertexBuffer.get(); vertexBuffer.get();// skip normal
						v1.set(vertexBuffer.get(), vertexBuffer.get(), vertexBuffer.get());
						vertexBuffer.get(); vertexBuffer.get(); vertexBuffer.get();// skip normal
						v2.set(vertexBuffer.get(), vertexBuffer.get(), vertexBuffer.get());
						vertexBuffer.get(); vertexBuffer.get(); vertexBuffer.get();// skip normal
						mesh.addFace(v0, v1, v2);
					}
					
					mesh.computeVertexNormals();
					String filename = String.format("objs/%d_%d_%d.obj", x, y, z);
					mesh.saveAsOBJ(filename);
				}
	}
}
