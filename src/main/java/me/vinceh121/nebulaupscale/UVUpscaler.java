package me.vinceh121.nebulaupscale;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.vinceh121.n2ae.model.NvxFileReader;
import me.vinceh121.n2ae.model.NvxFileWriter;
import me.vinceh121.n2ae.model.VertexType;

public class UVUpscaler {
	private final ExecutorService exec;
	private Path input, output;
	private int scale = 4;

	public UVUpscaler() {
		this(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
	}

	public UVUpscaler(ExecutorService exec) {
		this.exec = exec;
	}

	public void run() throws IOException {
		Files.walk(input).forEach(c -> {
			if (!c.getFileName().toString().endsWith(".nvx")) {
				return;
			}

			if (!c.toString().contains("guntower"))
				return;

			this.exec.submit(() -> {
				try {
					final Path out = output.resolve(input.relativize(c));
					Files.createDirectories(out.getParent());

					NvxFileReader reader = new NvxFileReader(new FileInputStream(c.toFile()));
					reader.readAll();
					reader.close();
					if (!reader.getTypes().contains(VertexType.UV0)) {
						return;
					}
					NvxFileWriter writer = new NvxFileWriter(new FileOutputStream(out.toFile()));
					reader.moveToWriter(writer);
//					for (final Vertex v : writer.getVertices()) {
//						v.getUv().get(0)[0] *= this.scale;
//						v.getUv().get(0)[1] *= this.scale;
//					}
					writer.writeHeaders();
					writer.writeData();
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		});
	}

	public Path getInput() {
		return input;
	}

	public void setInput(Path input) {
		this.input = input;
	}

	public Path getOutput() {
		return output;
	}

	public void setOutput(Path output) {
		this.output = output;
	}

	public int getScale() {
		return scale;
	}

	public void setScale(int scale) {
		this.scale = scale;
	}

	public ExecutorService getExecutorService() {
		return exec;
	}
}
