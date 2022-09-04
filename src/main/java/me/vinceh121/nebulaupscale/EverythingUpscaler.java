package me.vinceh121.nebulaupscale;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import me.vinceh121.n2ae.pkg.NnpkFileExtractor;
import me.vinceh121.n2ae.pkg.NnpkFileReader;
import me.vinceh121.n2ae.pkg.NnpkFileWriter;
import me.vinceh121.n2ae.texture.Block;
import me.vinceh121.n2ae.texture.BlockFormat;
import me.vinceh121.n2ae.texture.BlockType;
import me.vinceh121.n2ae.texture.NtxFileReader;
import me.vinceh121.n2ae.texture.NtxFileWriter;

public class EverythingUpscaler {
	private final ExecutorService exec;
	private Path dataArchive, extractionFolder, workingFolder, esrganPath, upscaledOutput;
	private int scale = 4;

	public static void main(String[] args) throws IOException, InterruptedException {
		EverythingUpscaler e = new EverythingUpscaler();
		e.setDataArchive(Paths.get("/home/vincent/Games/ProjectNomads/Project Nomads/Run/data-orig.npk"));
		e.setExtractionFolder(Paths.get("/tmp/data.n/"));
		e.setWorkingFolder(Paths.get("/tmp/data-upscaled.n/"));
		e.setEsrganPath(Paths.get("/home/vincent/Software/Real-ESRGAN/realesrgan-ncnn-vulkan"));
		e.setUpscaledOutput(Paths.get("/home/vincent/Games/ProjectNomads/Project Nomads/Run/data.npk"));
		Files.createDirectories(e.getExtractionFolder());
		Files.createDirectories(e.getWorkingFolder());
		e.run();
		System.exit(0);
	}

	public EverythingUpscaler() {
		this(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
	}

	public EverythingUpscaler(ExecutorService exec) {
		this.exec = exec;
	}

	public void run() throws IOException, InterruptedException {
		System.out.println("Extracting");
		this.extractArchive();
		System.out.println("Converting textures to PNG");
		this.convertAllTextures();
		this.waitForTasks();
		System.out.println("Upscaling Textures");
		this.upscaleTextures();
		this.convertTexturesBack();
		this.waitForTasks();
//		System.out.println("Upscaling UVs");
//		this.upscaleUVs();
//		this.waitForTasks();
		System.out.println("Repacking");
		this.moveWorking();
		this.repack();
		this.waitForTasks();

		System.out.println("Done!");
	}

	private void extractArchive() throws IOException {
		try (final InputStream in = Files.newInputStream(dataArchive)) {
			final NnpkFileReader r = new NnpkFileReader(in);
			r.readAll();

			final NnpkFileExtractor ex = new NnpkFileExtractor(in);
			ex.setOutput(this.extractionFolder.toFile());
			ex.extractAllFiles(r.getTableOfContents());
		}
	}

	private void convertAllTextures() throws IOException {
		Files.walk(this.extractionFolder).forEach(c -> {
			if (!c.getFileName().toString().endsWith(".ntx")) {
				return;
			}

			this.exec.submit(() -> {
				try (final InputStream in = Files.newInputStream(c)) {
					NtxFileReader read = new NtxFileReader(in);
					read.readHeader();
					read.readAllTextures();

					BufferedImage img = read.getTextures().get(0);
					try (final OutputStream out = Files
						.newOutputStream(c.resolveSibling(c.getFileName().toString().replace(".ntx", ".png")))) {
						ImageIO.write(img, "png", out);
					}
				} catch (IOException e) {
					System.err.println(c);
					e.printStackTrace();
				}
			});
		});
	}

	private void upscaleTextures() throws IOException, InterruptedException {
		ImageUpscaler up = new ImageUpscaler();
		up.setScale(this.scale);
		up.setInput(this.extractionFolder);
		up.setOutput(this.workingFolder);
		up.setEsrganPath(this.esrganPath);
		up.run();
		up.getExecutorService().shutdown();
		up.getExecutorService().awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
	}

	private void convertTexturesBack() throws IOException {
		Files.walk(this.workingFolder).forEach(c -> {
			if (!c.getFileName().toString().endsWith(".png")) {
				return;
			}

			this.exec.submit(() -> {
				try (OutputStream out = Files
					.newOutputStream(c.resolveSibling(c.getFileName().toString().replace(".png", ".ntx")))) {

					BufferedImage img = ImageIO.read(c.toFile());
					final BlockFormat fmt = img.getAlphaRaster() == null ? BlockFormat.ARGB4 : BlockFormat.ARGB4;
					byte[] data = NtxFileWriter.imageToRaw(img, img.getWidth(), img.getHeight(), fmt);
					ByteArrayOutputStream mipmaps = new ByteArrayOutputStream(img.getHeight() * img.getWidth() * 2);
					mipmaps.write(data);

					int offset = 328; // 10 block headers of 8 ints

					Block b = new Block();
					b.setWidth(img.getWidth());
					b.setHeight(img.getHeight());
					b.setDataOffset(offset);
					b.setDataLength(data.length);
					b.setFormat(fmt);
					b.setMipmapLevel(0);
					b.setDepth(1);
					b.setType(BlockType.TEXTURE_2D);

					offset += data.length;

					NtxFileWriter writer = new NtxFileWriter(out);
					writer.writeHeader(10);
					writer.writeBlock(b);

					for (int i = 1; i < 10; i++) {
						AffineTransform ts = AffineTransform.getScaleInstance(0.5, 0.5);
						AffineTransformOp op = new AffineTransformOp(ts, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
						img = op.filter(img, null);
						data = NtxFileWriter.imageToRaw(img, img.getWidth(), img.getHeight(), fmt);
						mipmaps.write(data);

						b = new Block();
						b.setWidth(img.getWidth());
						b.setHeight(img.getHeight());
						b.setDataOffset(offset);
						b.setDataLength(data.length);
						b.setFormat(fmt);
						b.setMipmapLevel(i);
						b.setDepth(1);
						b.setType(BlockType.TEXTURE_2D);
						writer.writeBlock(b);

						offset += data.length;

						if (b.getWidth() == 1 || b.getHeight() == 1) {
							break;
						}
					}
					mipmaps.write(new byte[4096]);
					out.write(mipmaps.toByteArray());

					Files.delete(c);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		});
	}

	private void upscaleUVs() throws IOException, InterruptedException {
		UVUpscaler uv = new UVUpscaler();
		uv.setInput(this.extractionFolder);
		uv.setOutput(this.workingFolder);
		uv.setScale(this.scale);
		uv.run();
		uv.getExecutorService().shutdown();
		uv.getExecutorService().awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
	}

	private void moveWorking() throws IOException {
		Files.walk(this.workingFolder).forEach(upscaled -> {
			if (!Files.isRegularFile(upscaled)) {
				return;
			}
			Path rel = this.workingFolder.relativize(upscaled);
			Path to = this.extractionFolder.resolve(rel);
			try {
				Files.copy(upscaled, to, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private void repack() throws IOException {
		try (OutputStream out = Files.newOutputStream(this.upscaledOutput)) {
			NnpkFileWriter writer = new NnpkFileWriter(out);
			writer.writeArchive(this.extractionFolder.toFile());
		}
	}

	private void waitForTasks() {
		ThreadPoolExecutor e = (ThreadPoolExecutor) this.exec;
		while (e.getActiveCount() > 0) {
			Thread.yield();
		}
	}

	public Path getDataArchive() {
		return dataArchive;
	}

	public void setDataArchive(Path dataArchive) {
		this.dataArchive = dataArchive;
	}

	public Path getExtractionFolder() {
		return extractionFolder;
	}

	public void setExtractionFolder(Path extractionFolder) {
		this.extractionFolder = extractionFolder;
	}

	public Path getWorkingFolder() {
		return workingFolder;
	}

	public void setWorkingFolder(Path workingFolder) {
		this.workingFolder = workingFolder;
	}

	public Path getEsrganPath() {
		return esrganPath;
	}

	public void setEsrganPath(Path esrganPath) {
		this.esrganPath = esrganPath;
	}

	public Path getUpscaledOutput() {
		return upscaledOutput;
	}

	public void setUpscaledOutput(Path upscaledOutput) {
		this.upscaledOutput = upscaledOutput;
	}

	public int getScale() {
		return scale;
	}

	public void setScale(int scale) {
		this.scale = scale;
	}
}
