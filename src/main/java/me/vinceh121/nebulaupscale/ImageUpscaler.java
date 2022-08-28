package me.vinceh121.nebulaupscale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageUpscaler {
	private final ExecutorService exec;
	private String esrganPath;
	private Path input, output;

	public ImageUpscaler() {
		this(Executors.newFixedThreadPool(4));
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
						.exec(new String[] { this.esrganPath, "-i", c.toAbsolutePath().toString(), "-o",
								out.toString() }, null, Paths.get(esrganPath).getParent().toAbsolutePath().toFile());
					p.waitFor();
					System.out.println(out.getFileName() + ": " + (System.currentTimeMillis() - start) + "ms");
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
			});
		});
	}

	public String getEsrganPath() {
		return esrganPath;
	}

	public void setEsrganPath(String esrganPath) {
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
}
