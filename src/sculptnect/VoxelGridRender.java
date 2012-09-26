package sculptnect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.vecmath.Point3i;
import javax.vecmath.Tuple3i;
import javax.vecmath.Vector3f;

import com.jogamp.common.nio.Buffers;

public class VoxelGridRender {
	private static final int CELL_SIZE = 20;
	private static final int NUM_THREADS = 4;

	VoxelGrid grid;
	BufferCell[][][] bufferCells;
	Set<BufferCell> dirtyCells = Collections.synchronizedSet(new HashSet<BufferCell>());
	Set<BufferCell> visibleCells = new HashSet<BufferCell>();

	ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

	ReentrantLock dirtyMarkingLock = new ReentrantLock();

	BlockingSet<BufferCell> waitingBufferCellSet = new BlockingSet<VoxelGridRender.BufferCell>();
	BlockingSet<BufferCell> completedBufferCellSet = new BlockingSet<VoxelGridRender.BufferCell>();

	BlockingQueue<FloatBuffer> floatBufferQueue = new ArrayBlockingQueue<FloatBuffer>(NUM_THREADS, false);

	Tuple3i dimensions = new Point3i();

	private class BufferCell {
		// The position of this cell in the buffer grid
		Tuple3i position = new Point3i();

		// The lower indices of the voxel grid for this cell
		Tuple3i lowerIndices = new Point3i();
		// The upper indices of the voxel grid for this cell
		Tuple3i upperIndices = new Point3i();

		// The buffer object name used as handle in OpenGL
		int bufferName;
		// The number of indices this buffer object contains
		int numIndices;

		// Float buffer (temporarily) containing this buffer cell's point data
		FloatBuffer floatBuffer;
		int numNewIndices;

		boolean dirty;

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof BufferCell) {
				return position.equals(((BufferCell) obj).position);
			}

			return false;
		}

		@Override
		public int hashCode() {
			return position.hashCode();
		}

	}

	private class BufferCellPointCreator implements Runnable {
		VoxelGrid grid;

		Vector3f normal = new Vector3f();

		public BufferCellPointCreator(VoxelGrid grid) {
			this.grid = grid;
		}

		@Override
		public void run() {
			try {
				while (true) {
					BufferCell cell = waitingBufferCellSet.take();

					FloatBuffer floatBuffer = floatBufferQueue.take();
					floatBuffer.clear();

					cell.numNewIndices = 0;
					for (int x = cell.lowerIndices.x; x < cell.upperIndices.x; x++) {
						for (int y = cell.lowerIndices.y; y < cell.upperIndices.y; y++) {
							for (int z = cell.lowerIndices.z; z < cell.upperIndices.z; z++) {
								// Skip voxel if it's empty
								if (grid.isAir(x, y, z))
									continue;

								// Determine if voxel is completely inside by
								// examining its neighbors
								for (int i = 0; i < 6; i++) {
									int[] offset = VoxelGrid.offsets[i];
									int xoff = x + offset[0];
									int yoff = y + offset[1];
									int zoff = z + offset[2];

									boolean inside = xoff >= 0 && xoff < grid.width && yoff >= 0 && yoff < grid.height && zoff >= 0 && zoff < grid.depth;
									if (!inside || (grid.getVoxel(xoff, yoff, zoff) != grid.getVoxel(x, y, z))) {
										cell.numNewIndices++;

										// Put vertex data into buffer
										floatBuffer.put(x);
										floatBuffer.put(y);
										floatBuffer.put(z);

										// Put normal for the vertex into buffer
										Vector3f n = this.normalForVoxel(x, y, z, normal);
										floatBuffer.put(n.x);
										floatBuffer.put(n.y);
										floatBuffer.put(n.z);

										break;
									}
								}
							}
						}
					}

					floatBuffer.rewind();
					cell.floatBuffer = floatBuffer;
					completedBufferCellSet.add(cell);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		private Vector3f normalForVoxel(int x, int y, int z, Vector3f normal) {
			float numAdded = 0;
			normal.set(0, 0, 0);

			// Iterate through the 26 neighbors of the voxel
			for (int xd = -2; xd < 3; xd++) {
				for (int yd = -2; yd < 3; yd++) {
					for (int zd = -2; zd < 3; zd++) {
						int xoff = x + xd;
						int yoff = y + yd;
						int zoff = z + zd;

						if (xoff >= 0 && xoff < grid.width && yoff >= 0 && yoff < grid.height && zoff >= 0 && zoff < grid.depth) {
							if (grid.isAir(xoff, yoff, zoff)) {
								// If the neighbor voxel is empty, add the
								// direction
								// to it to the normal
								normal.x += xd;
								normal.y += yd;
								normal.z += zd;
								numAdded++;
							}
						}
					}
				}
			}

			// Take the average of all summed vectors and normalize the result
			//normal.scale(1.0f / numAdded);
			//normal.normalize();

			return normal;
		}
	}

	public VoxelGridRender(VoxelGrid grid) {
		this.grid = grid;

		// Add floatbuffers to queue, and add BufferCell creators to executor
		for (int i = 0; i < NUM_THREADS; i++) {
			floatBufferQueue.offer(ByteBuffer.allocateDirect(CELL_SIZE * CELL_SIZE * CELL_SIZE * 6 * 4 / 2).order(ByteOrder.nativeOrder()).asFloatBuffer());
			executor.submit(new BufferCellPointCreator(grid));
		}

		// Calculate buffer dimensions
		dimensions.x = (int) Math.ceil((double) grid.width / CELL_SIZE);
		dimensions.y = (int) Math.ceil((double) grid.height / CELL_SIZE);
		dimensions.z = (int) Math.ceil((double) grid.depth / CELL_SIZE);

		// Create the buffer cells and initialize them
		bufferCells = new BufferCell[dimensions.x][dimensions.y][dimensions.z];
		for (int x = 0; x < dimensions.x; x++) {
			for (int y = 0; y < dimensions.y; y++) {
				for (int z = 0; z < dimensions.z; z++) {
					BufferCell cell = new BufferCell();
					bufferCells[x][y][z] = cell;

					// Set cell position and indices bounds
					cell.position.set(x, y, z);
					cell.lowerIndices.set(x * CELL_SIZE, y * CELL_SIZE, z * CELL_SIZE);
					cell.upperIndices.set(Math.min((x + 1) * CELL_SIZE, grid.width), Math.min((y + 1) * CELL_SIZE, grid.height), Math.min((z + 1) * CELL_SIZE, grid.depth));
				}
			}
		}
	}
	
	public void refresh() {
		beginVoxelMarking();
		
		for (int x = 0; x < dimensions.x; x++) {
			for (int y = 0; y < dimensions.y; y++) {
				for (int z = 0; z < dimensions.z; z++) {
					BufferCell cell = bufferCells[x][y][z];
					dirtyCells.add(cell);
				}
			}
		}
		
		endVoxelMarking();
	}
	
	public void beginVoxelMarking() {
		dirtyMarkingLock.lock();
	}

	public void endVoxelMarking() {
		waitingBufferCellSet.addAll(dirtyCells);

		for (BufferCell cell : dirtyCells) {
			cell.dirty = false;
		}
		dirtyCells.clear();

		dirtyMarkingLock.unlock();
	}

	public void markVoxelDirty(int x, int y, int z) {
		BufferCell cell = bufferCells[x / CELL_SIZE][y / CELL_SIZE][z / CELL_SIZE];
		if (!cell.dirty) {
			cell.dirty = true;
			dirtyCells.add(cell);
		}
	}

	public void updateDirtyCells(GL2 gl) {
		long startTime = System.nanoTime();
		final long frameTime = (long) (1000000000 * (1.0f / 100.0f));

		try {
			while (true) {
				long timeLeft = frameTime - (System.nanoTime() - startTime);
				if (timeLeft <= 0) {
					break;
				}

				BufferCell cell = completedBufferCellSet.poll(timeLeft);
				if (cell == null) {
					break;
				}

				// If this cell doesn't contain any points, remove it from the
				// visible buffer cell set
				if (cell.numNewIndices == 0) {
					visibleCells.remove(cell);
				} else {
					visibleCells.add(cell);
				}

				FloatBuffer floatBuffer = cell.floatBuffer;
				cell.floatBuffer = null;

				if (cell.bufferName == 0) {
					// Generate and set a buffer object name for this cell
					int[] buf = new int[1];
					gl.glGenBuffers(1, buf, 0);
					cell.bufferName = buf[0];
				}

				// Upload the vertex and normal data to the buffer
				gl.glBindBuffer(GL.GL_ARRAY_BUFFER, cell.bufferName);
				gl.glBufferData(GL.GL_ARRAY_BUFFER, cell.numNewIndices * 6 * Buffers.SIZEOF_FLOAT, floatBuffer, GL.GL_STATIC_DRAW);
				gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);

				cell.numIndices = cell.numNewIndices;
				floatBufferQueue.offer(floatBuffer);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void draw(GL2 gl) {
		gl.glColor3f(.3f, .7f, .7f);

		// Enable the vertex and normal arrays
		gl.glEnableClientState(GL2.GL_VERTEX_ARRAY);
		gl.glEnableClientState(GL2.GL_NORMAL_ARRAY);

		// Loop through visible cells and draw them
		for (BufferCell cell : visibleCells) {
			// Bind buffer containing points and normals
			gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, cell.bufferName);

			// Specify vertex and normal data
			int stride = Buffers.SIZEOF_FLOAT * 6;
			gl.glVertexPointer(3, GL2.GL_FLOAT, stride, 0);
			gl.glNormalPointer(GL2.GL_FLOAT, stride, Buffers.SIZEOF_FLOAT * 3);

			// Draw the points
			gl.glDrawArrays(GL2.GL_POINTS, 0, cell.numIndices);
		}

		// Unbind the buffer data
		gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);

		// Disable vertex and normal arrays
		gl.glDisableClientState(GL2.GL_NORMAL_ARRAY);
		gl.glDisableClientState(GL2.GL_VERTEX_ARRAY);
	}
}
