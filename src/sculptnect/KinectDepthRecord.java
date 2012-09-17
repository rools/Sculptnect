package sculptnect;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPOutputStream;

public class KinectDepthRecord {
	private GZIPOutputStream gzipOutputStream;
	private FileOutputStream fileOutputStream;
	private BufferedOutputStream bufferedOutputStream;

	public KinectDepthRecord(String file) throws IOException {
		fileOutputStream = new FileOutputStream(file);
		gzipOutputStream = new GZIPOutputStream(fileOutputStream);
		bufferedOutputStream = new BufferedOutputStream(gzipOutputStream);
	}

	public void addFrame(ByteBuffer frame) throws IOException {
		frame.rewind();
		for (int i = 0; i < 640 * 480 * 2; ++i) {
			bufferedOutputStream.write(frame.get());
		}
	}

	public void close() {
		try {
			bufferedOutputStream.close();
			gzipOutputStream.close();
			fileOutputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
