package me.vinceh121.nebulaupscale;

import java.awt.image.BufferedImage;

public final class Dithering {

	private Dithering() {
	}

	public static BufferedImage dither(BufferedImage src) {
		for (int x = 0; x < src.getWidth(); x++) {
			for (int y = 0; y < src.getHeight(); y++) {
				final int origPixel = src.getRGB(x, y);
				// reduce, but still encode on 8 bits
				final int newPixel = origPixel & 0xF0F0F0F0;
				src.setRGB(x, y, newPixel);

				// original values
				final short oalpha = (short) (origPixel >> 24);
				final short ored = (short) (origPixel >> 16 & 0xFF);
				final short ogreen = (short) (origPixel >> 8 & 0xFF);
				final short oblue = (short) (origPixel & 0xFF);

				// error values
				final short ealpha = (short) (oalpha & 0xF);
				final short ered = (short) (ored & 0xF);
				final short egreen = (short) (ogreen & 0xF);
				final short eblue = (short) (oblue & 0xF);

				if (x > 0 && y > 0 && x < src.getWidth() - 1 && y < src.getHeight() - 1) {
					src.setRGB(x + 1, y, errorPixel(src.getRGB(x + 1, y), ealpha, ered, egreen, eblue, 7));
					src.setRGB(x - 1, y + 1, errorPixel(src.getRGB(x - 1, y + 1), ealpha, ered, egreen, eblue, 3));
					src.setRGB(x, y + 1, errorPixel(src.getRGB(x, y + 1), ealpha, ered, egreen, eblue, 5));
					src.setRGB(x + 1, y + 1, errorPixel(src.getRGB(x + 1, y + 1), ealpha, ered, egreen, eblue, 1));
				}
			}
		}
		return src;
	}

	// from
	// https://gitbox.apache.org/repos/asf?p=commons-imaging.git;a=blob;f=src/main/java/org/apache/commons/imaging/palette/Dithering.java;h=08c34069e4d22dcbc299eb65f578cf3a9a7f589e;hb=HEAD
	public static int errorPixel(int orig, short ealpha, short ered, short egreen, short eblue, int mul) {
		int a = (orig >> 24) & 0xff;
		int r = (orig >> 16) & 0xff;
		int g = (orig >> 8) & 0xff;
		int b = orig & 0xff;

		a += ealpha * mul / 16;
		r += ered * mul / 16;
		g += egreen * mul / 16;
		b += eblue * mul / 16;

		if (a < 0) {
			a = 0;
		} else if (a > 0xff) {
			a = 0xff;
		}
		if (r < 0) {
			r = 0;
		} else if (r > 0xff) {
			r = 0xff;
		}
		if (g < 0) {
			g = 0;
		} else if (g > 0xff) {
			g = 0xff;
		}
		if (b < 0) {
			b = 0;
		} else if (b > 0xff) {
			b = 0xff;
		}

		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
