package me.vinceh121.nebulaupscale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageUpscaler {
	public static final String ITERATION_PREFIX = "ITER_";
	private final ExecutorService exec;
	private Path input, output, esrganPath;
	private int scale = 4, scheduled, done, timeTotal, iterations, iterationsScale;

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

			this.scheduled++;
			exec.submit(() -> {
				try {
					final Path out = output.resolve(input.relativize(c));
					Path in = c.toAbsolutePath();
					Files.createDirectories(out.getParent());
					long start = System.currentTimeMillis();
					for (int i = this.iterations; i > 1; i--) {
						Path iterOut = out.resolveSibling(ITERATION_PREFIX + i + "-" + out.getFileName().toString());
						Process p = Runtime.getRuntime()
							.exec(new String[] { this.esrganPath.toAbsolutePath().toString(), "-i", in.toString(), "-o",
									iterOut.toAbsolutePath().toString(), "-s", String.valueOf(this.iterationsScale), "-f",
									"png" }, null, esrganPath.getParent().toAbsolutePath().toFile());
						p.waitFor();
						if (p.exitValue() != 0) {
							System.out.println("fucked " + in);
						}
						in = iterOut;
					}
					Process p = Runtime.getRuntime()
						.exec(new String[] { this.esrganPath.toAbsolutePath().toString(), "-i", in.toString(), "-o",
								out.toString(), "-s", String.valueOf(this.scale) },
								null,
								esrganPath.getParent().toAbsolutePath().toFile());
					p.waitFor();
					if (p.exitValue() != 0) {
						System.out.println("fucked " + in);
					}
					this.done++;
					final long time = System.currentTimeMillis() - start;
					this.timeTotal += time;
					final long avg = (this.timeTotal / this.done);
					System.out.println(out);
					System.out.println("\t" + time + "ms\tavg: " + avg + "ms");
					System.out.println(
							"\t" + this.done + "/" + this.scheduled + "\t" + (this.done * 100f / this.scheduled) + "%");
					System.out.println("\tETA:\t" + Duration.ofMillis(avg * (this.scheduled - this.done)));
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
			});
		});
		Files.walk(this.output).forEach(c -> {
			if (c.getFileName().toString().startsWith(ITERATION_PREFIX)) {
				try {
					Files.delete(c);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
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

	public int getIterations() {
		return iterations;
	}

	public void setIterations(int iterations) {
		this.iterations = iterations;
	}

	public int getIterationsScale() {
		return iterationsScale;
	}

	public void setIterationsScale(int iterationsScale) {
		this.iterationsScale = iterationsScale;
	}

	public ExecutorService getExecutorService() {
		return this.exec;
	}
}
