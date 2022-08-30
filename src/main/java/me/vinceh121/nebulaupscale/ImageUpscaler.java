package me.vinceh121.nebulaupscale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageUpscaler {
	private final ExecutorService exec;
	private Path input, output, esrganPath;
	private int scale = 4;

	public ImageUpscaler() {
		this(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() / 5)); // a single realgan instance
																							// uses 5 threads by default
	}

	public ImageUpscaler(ExecutorService exec) {
		this.exec = exec;
	}

	public void run() throws IOException {
		Files.walk(input).forEach(c -> {
			try {
				if (!"image/png".equals(Files.probeContentType(c))) {
					return;
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			exec.submit(() -> {
				try {
					final Path out = output.resolve(input.relativize(c));
					Files.createDirectories(out.getParent());
					long start = System.currentTimeMillis();
					Process p = Runtime.getRuntime()
						.exec(new String[] { this.esrganPath.toAbsolutePath().toString(), "-i",
								c.toAbsolutePath().toString(), "-o", out.toString(), "-s", String.valueOf(this.scale) },
								null,
								esrganPath.getParent().toAbsolutePath().toFile());
					p.waitFor();
					System.out.println(out.getFileName() + ": " + (System.currentTimeMillis() - start) + "ms");
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
			});
		});
	}

	public Path getEsrganPath() {
		return esrganPath;
	}

	public void setEsrganPath(Path esrganPath) {
		this.esrganPath = esrganPath;
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
		return this.exec;
	}
}
