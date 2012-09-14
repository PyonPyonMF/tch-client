/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.awt.RenderingHints;
import java.io.*;
import java.nio.*;
import java.lang.reflect.*;
import java.util.prefs.*;
import java.util.*;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.image.*;

public class Utils {
    public static final java.nio.charset.Charset utf8 = java.nio.charset.Charset.forName("UTF-8");
    public static final java.nio.charset.Charset ascii = java.nio.charset.Charset.forName("US-ASCII");
    public static final java.awt.image.ColorModel rgbm = java.awt.image.ColorModel.getRGBdefault();
    private static Preferences prefs = null;

    static Coord imgsz(BufferedImage img) {
	return(new Coord(img.getWidth(), img.getHeight()));
    }
	
    public static void defer(final Runnable r) {
	Defer.later(new Defer.Callable<Object>() {
		public Object call() {
		    r.run();
		    return(null);
		}
	    });
    }
	
    static void drawgay(BufferedImage t, BufferedImage img, Coord c) {
	Coord sz = imgsz(img);
	for(int y = 0; y < sz.y; y++) {
	    for(int x = 0; x < sz.x; x++) {
		int p = img.getRGB(x, y);
		if(Utils.rgbm.getAlpha(p) > 128) {
		    if((p & 0x00ffffff) == 0x00ff0080)
			t.setRGB(x + c.x, y + c.y, 0);
		    else
			t.setRGB(x + c.x, y + c.y, p);
		}
	    }
	}
    }
	
    public static int drawtext(Graphics g, String text, Coord c) {
	java.awt.FontMetrics m = g.getFontMetrics();
	g.drawString(text, c.x, c.y + m.getAscent());
	return(m.getHeight());
    }
	
    static Coord textsz(Graphics g, String text) {
	java.awt.FontMetrics m = g.getFontMetrics();
	java.awt.geom.Rectangle2D ts = m.getStringBounds(text, g);
	return(new Coord((int)ts.getWidth(), (int)ts.getHeight()));
    }
	
    static void aligntext(Graphics g, String text, Coord c, double ax, double ay) {
	java.awt.FontMetrics m = g.getFontMetrics();
	java.awt.geom.Rectangle2D ts = m.getStringBounds(text, g);
	g.drawString(text, (int)(c.x - ts.getWidth() * ax), (int)(c.y + m.getAscent() - ts.getHeight() * ay));
    }
    
    public static String fpformat(int num, int div, int dec) {
	StringBuilder buf = new StringBuilder();
	boolean s = false;
	if(num < 0) {
	    num = -num; s = true;
	}
	for(int i = 0; i < div - dec; i++)
	    num /= 10;
	for(int i = 0; i < dec; i++) {
	    buf.append((char)('0' + (num % 10)));
	    num /= 10;
	}
	buf.append('.');
	if(num == 0) {
	    buf.append('0');
	} else {
	    while(num > 0) {
		buf.append((char)('0' + (num % 10)));
		num /= 10;
	    }
	}
	if(s)
	    buf.append('-');
	return(buf.reverse().toString());
    }
    
    static void line(Graphics g, Coord c1, Coord c2) {
	g.drawLine(c1.x, c1.y, c2.x, c2.y);
    }
	
    static void AA(Graphics g) {
	java.awt.Graphics2D g2 = (java.awt.Graphics2D)g;
	g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);		
    }
	
    static synchronized Preferences prefs() {
	if(prefs == null) {
	    Preferences node = Preferences.userNodeForPackage(Utils.class);
	    if(Config.prefspec != null)
		node = node.node(Config.prefspec);
	    prefs = node;
	}
	return(prefs);
    }

    static String getpref(String prefname, String def) {
	try {
	    return(prefs().get(prefname, def));
	} catch(SecurityException e) {
	    return(def);
	}
    }
	
    static void setpref(String prefname, String val) {
	try {
	    prefs().put(prefname, val);
	} catch(SecurityException e) {
	}
    }
    
    static boolean getprefb(String prefname, boolean def) {
	try {
	    return(prefs().getBoolean(prefname, def));
	} catch(SecurityException e) {
	    return(def);
	}
    }
    
    static void setprefb(String prefname, boolean val) {
	try {
	    prefs().putBoolean(prefname, val);
	} catch(SecurityException e) {
	}
    }

    static Coord getprefc(String prefname, Coord def) {
	try {
	    String val = prefs().get(prefname, null);
	    if(val == null)
		return(def);
	    int x = val.indexOf('x');
	    if(x < 0)
		return(def);
	    return(new Coord(Integer.parseInt(val.substring(0, x)), Integer.parseInt(val.substring(x + 1))));
	} catch(SecurityException e) {
	    return(def);
	}
    }
    
    static void setprefc(String prefname, Coord val) {
	try {
	    prefs().put(prefname, val.x + "x" + val.y);
	} catch(SecurityException e) {
	}
    }

    static byte[] getprefb(String prefname, byte[] def) {
	try {
	    return(prefs().getByteArray(prefname, def));
	} catch(SecurityException e) {
	    return(def);
	}
    }
	
    static void setprefb(String prefname, byte[] val) {
	try {
	    prefs().putByteArray(prefname, val);
	} catch(SecurityException e) {
	}
    }
    
    public static String getprop(String propname, String def) {
	try {
	    return(System.getProperty(propname, def));
	} catch(SecurityException e) {
	    return(def);
	}
    }
	
    static int ub(byte b) {
	if(b < 0)
	    return(256 + b);
	else
	    return(b);
    }
	
    static byte sb(int b) {
	if(b > 127)
	    return((byte)(-256 + b));
	else
	    return((byte)b);
    }
	
    static int uint16d(byte[] buf, int off) {
	return(ub(buf[off]) + (ub(buf[off + 1]) * 256));
    }
	
    static int int16d(byte[] buf, int off) {
	int u = uint16d(buf, off);
	if(u > 32767)
	    return(-65536 + u);
	else
	    return(u);
    }
	
    static long uint32d(byte[] buf, int off) {
	return(ub(buf[off]) + (ub(buf[off + 1]) * 256) + (ub(buf[off + 2]) * 65536) + (ub(buf[off + 3]) * 16777216l));
    }
	
    static void uint32e(long num, byte[] buf, int off) {
	buf[off] = sb((int)(num & 0xff));
	buf[off + 1] = sb((int)((num & 0xff00) >> 8));
	buf[off + 2] = sb((int)((num & 0xff0000) >> 16));
	buf[off + 3] = sb((int)((num & 0xff000000) >> 24));
    }
	
    static int int32d(byte[] buf, int off) {
	long u = uint32d(buf, off);
	if(u > 0x7fffffffL)
	    return((int)(u - 0x100000000L));
	else
	    return((int)u);
    }
    
    public static long int64d(byte[] buf, int off) {
	long b = 0;
	for(int i = 0; i < 7; i++)
	    b |= ((long)ub(buf[i])) << (i * 8);
	int h = ub(buf[7]);
	if(h < 128)
	    return(b | ((long)h << 56));
	else
	    return(0L + ((long)(-255 + h) * 0x0100000000000000L) + (-0x0100000000000000L + b));
    }
	
    static void int32e(int num, byte[] buf, int off) {
	if(num < 0)
	    uint32e(0x100000000L + ((long)num), buf, off);
	else
	    uint32e(num, buf, off);
    }
	
    static void uint16e(int num, byte[] buf, int off) {
	buf[off] = sb(num & 0xff);
	buf[off + 1] = sb((num & 0xff00) >> 8);
    }
	
    static String strd(byte[] buf, int[] off) {
	int i;
	for(i = off[0]; buf[i] != 0; i++);
	String ret;
	try {
	    ret = new String(buf, off[0], i - off[0], "utf-8");
	} catch(UnsupportedEncodingException e) {
	    throw(new IllegalArgumentException(e));
	}
	off[0] = i + 1;
	return(ret);
    }
    
    static double floatd(byte[] buf, int off) {
	int e = buf[off];
	long t = uint32d(buf, off + 1);
	int m = (int)(t & 0x7fffffffL);
	boolean s = (t & 0x80000000L) != 0;
	if(e == -128) {
	    if(m == 0)
		return(0.0);
	    throw(new RuntimeException("Invalid special float encoded (" + m + ")"));
	}
	double v = (((double)m) / 2147483648.0) + 1.0;
	if(s)
	    v = -v;
	return(Math.pow(2.0, e) * v);
    }
	
    static char num2hex(int num) {
	if(num < 10)
	    return((char)('0' + num));
	else
	    return((char)('A' + num - 10));
    }
	
    static int hex2num(char hex) {
	if((hex >= '0') && (hex <= '9'))
	    return(hex - '0');
	else if((hex >= 'a') && (hex <= 'f'))
	    return(hex - 'a' + 10);
	else if((hex >= 'A') && (hex <= 'F'))
	    return(hex - 'A' + 10);
	else
	    throw(new IllegalArgumentException());
    }

    static String byte2hex(byte[] in) {
	StringBuilder buf = new StringBuilder();
	for(byte b : in) {
	    buf.append(num2hex((b & 0xf0) >> 4));
	    buf.append(num2hex(b & 0x0f));
	}
	return(buf.toString());
    }

    static byte[] hex2byte(String hex) {
	if(hex.length() % 2 != 0)
	    throw(new IllegalArgumentException("Invalid hex-encoded string"));
	byte[] ret = new byte[hex.length() / 2];
	for(int i = 0, o = 0; i < hex.length(); i += 2, o++)
	    ret[o] = (byte)((hex2num(hex.charAt(i)) << 4) | hex2num(hex.charAt(i + 1)));
	return(ret);
    }
    
    private final static String base64set = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private final static int[] base64rev;
    static {
	int[] rev = new int[128];
	for(int i = 0; i < 128; rev[i++] = -1);
	for(int i = 0; i < base64set.length(); i++)
	    rev[base64set.charAt(i)] = i;
	base64rev = rev;
    }
    public static String base64enc(byte[] in) {
	StringBuilder buf = new StringBuilder();
	int p = 0;
	while(in.length - p >= 3) {
	    buf.append(base64set.charAt( (in[p + 0] & 0xfc) >> 2));
	    buf.append(base64set.charAt(((in[p + 0] & 0x03) << 4) | ((in[p + 1] & 0xf0) >> 4)));
	    buf.append(base64set.charAt(((in[p + 1] & 0x0f) << 2) | ((in[p + 2] & 0xc0) >> 6)));
	    buf.append(base64set.charAt(  in[p + 2] & 0x3f));
	    p += 3;
	}
	if(in.length == p + 1) {
	    buf.append(base64set.charAt( (in[p + 0] & 0xfc) >> 2));
	    buf.append(base64set.charAt( (in[p + 0] & 0x03) << 4));
	    buf.append("==");
	} else if(in.length == p + 2) {
	    buf.append(base64set.charAt( (in[p + 0] & 0xfc) >> 2));
	    buf.append(base64set.charAt(((in[p + 0] & 0x03) << 4) | ((in[p + 1] & 0xf0) >> 4)));
	    buf.append(base64set.charAt( (in[p + 1] & 0x0f) << 2));
	    buf.append("=");
	}
	return(buf.toString());
    }
    public static byte[] base64dec(String in) {
	ByteArrayOutputStream buf = new ByteArrayOutputStream();
	int cur = 0, b = 8;
	for(int i = 0; i < in.length(); i++) {
	    char c = in.charAt(i);
	    if(c >= 128)
		throw(new IllegalArgumentException());
	    if(c == '=')
		break;
	    int d = base64rev[c];
	    if(d == -1)
		throw(new IllegalArgumentException());
	    b -= 6;
	    if(b <= 0) {
		cur |= d >> -b;
		buf.write(cur);
		b += 8;
		cur = 0;
	    }
	    cur |= d << b;
	}
	return(buf.toByteArray());
    }
	
    public static String[] splitwords(String text) {
	ArrayList<String> words = new ArrayList<String>();
	StringBuilder buf = new StringBuilder();
	String st = "ws";
	int i = 0;
	while(i < text.length()) {
	    char c = text.charAt(i);
	    if(st == "ws") {
		if(!Character.isWhitespace(c))
		    st = "word";
		else
		    i++;
	    } else if(st == "word") {
		if(c == '"') {
		    st = "quote";
		    i++;
		} else if(c == '\\') {
		    st = "squote";
		    i++;
		} else if(Character.isWhitespace(c)) {
		    words.add(buf.toString());
		    buf = new StringBuilder();
		    st = "ws";
		} else {
		    buf.append(c);
		    i++;
		}
	    } else if(st == "quote") {
		if(c == '"') {
		    st = "word";
		    i++;
		} else if(c == '\\') {
		    st = "sqquote";
		    i++;
		} else {
		    buf.append(c);
		    i++;
		}
	    } else if(st == "squote") {
		buf.append(c);
		i++;
		st = "word";
	    } else if(st == "sqquote") {
		buf.append(c);
		i++;
		st = "quote";
	    }
	}
	if(st == "word")
	    words.add(buf.toString());
	if((st != "ws") && (st != "word"))
	    return(null);
	return(words.toArray(new String[0]));
    }
	
    public static String[] splitlines(String text) {
	ArrayList<String> ret = new ArrayList<String>();
	int p = 0;
	while(true) {
	    int p2 = text.indexOf('\n', p);
	    if(p2 < 0) {
		ret.add(text.substring(p));
		break;
	    }
	    ret.add(text.substring(p, p2));
	    p = p2 + 1;
	}
	return(ret.toArray(new String[0]));
    }

    static int atoi(String a) {
	try {
	    return(Integer.parseInt(a));
	} catch(NumberFormatException e) {
	    return(0);
	}
    }
    
    static void readtileof(InputStream in) throws IOException {
        byte[] buf = new byte[4096];
        while(true) {
            if(in.read(buf, 0, buf.length) < 0)
                return;
        }
    }
    
    static byte[] readall(InputStream in) throws IOException {
	byte[] buf = new byte[4096];
	int off = 0;
	while(true) {
	    if(off == buf.length) {
		byte[] n = new byte[buf.length * 2];
		System.arraycopy(buf, 0, n, 0, buf.length);
		buf = n;
	    }
	    int ret = in.read(buf, off, buf.length - off);
	    if(ret < 0) {
		byte[] n = new byte[off];
		System.arraycopy(buf, 0, n, 0, off);
		return(n);
	    }
	    off += ret;
	}
    }
    
    private static void dumptg(ThreadGroup tg, PrintWriter out, int indent) {
	for(int o = 0; o < indent; o++)
	    out.print("    ");
	out.println("G: \"" + tg.getName() + "\"");
	Thread[] ths = new Thread[tg.activeCount() * 2];
	ThreadGroup[] tgs = new ThreadGroup[tg.activeGroupCount() * 2];
	int nt = tg.enumerate(ths, false);
	int ng = tg.enumerate(tgs, false);
	for(int i = 0; i < nt; i++) {
	    Thread ct = ths[i];
	    for(int o = 0; o < indent + 1; o++)
		out.print("    ");
	    out.println("T: \"" + ct.getName() + "\"");
	}
	for(int i = 0; i < ng; i++) {
	    ThreadGroup cg = tgs[i];
	    dumptg(cg, out, indent + 1);
	}
    }

    public static void dumptg(ThreadGroup tg, PrintWriter out) {
	if(tg == null) {
	    tg = Thread.currentThread().getThreadGroup();
	    while(tg.getParent() != null)
		tg = tg.getParent();
	}
	dumptg(tg, out, 0);
	out.flush();
    }

    public static Resource myres(Class<?> c) {
	ClassLoader cl = c.getClassLoader();
	if(cl instanceof Resource.ResClassLoader) {
	    return(((Resource.ResClassLoader)cl).getres());
	} else {
	    return(null);
	}
    }
    
    public static String titlecase(String str) {
	return(Character.toTitleCase(str.charAt(0)) + str.substring(1));
    }
    
    public static Color contrast(Color col) {
	int max = Math.max(col.getRed(), Math.max(col.getGreen(), col.getBlue()));
	if(max > 128) {
	    return(new Color(col.getRed() / 2, col.getGreen() / 2, col.getBlue() / 2, col.getAlpha()));
	} else if(max == 0) {
	    return(Color.WHITE);
	} else {
	    int f = 128 / max;
	    return(new Color(col.getRed() * f, col.getGreen() * f, col.getBlue() * f, col.getAlpha()));
	}
    }

    public static Color clipcol(int r, int g, int b, int a) {
	if(r < 0)   r = 0;
	if(r > 255) r = 255;
	if(g < 0)   g = 0;
	if(g > 255) g = 255;
	if(b < 0)   b = 0;
	if(b > 255) b = 255;
	if(a < 0)   a = 0;
	if(a > 255) a = 255;
	return(new Color(r, g, b, a));
    }

    public static BufferedImage outline(BufferedImage img, Color col) {
	Coord sz = imgsz(img).add(2, 2);
	BufferedImage ol = TexI.mkbuf(sz);
	Object fcol = ol.getColorModel().getDataElements(col.getRGB(), null);
	Raster src = img.getRaster();
	WritableRaster dst = ol.getRaster();
	for(int y = 0; y < sz.y; y++) {
	    for(int x = 0; x < sz.x; x++) {
		boolean t;
		if((y == 0) || (x == 0) || (y == sz.y - 1) || (x == sz.x - 1)) {
		    t = true;
		} else {
		    t = src.getSample(x - 1, y - 1, 3) < 250;
		}
		if(!t)
		    continue;
		if(((x > 1) && (y > 0) && (y < sz.y - 1) && (src.getSample(x - 2, y - 1, 3) >= 250)) ||
		   ((x > 0) && (y > 1) && (x < sz.x - 1) && (src.getSample(x - 1, y - 2, 3) >= 250)) ||
		   ((x < sz.x - 2) && (y > 0) && (y < sz.y - 1) && (src.getSample(x, y - 1, 3) >= 250)) ||
		   ((x > 0) && (y < sz.y - 2) && (x < sz.x - 1) && (src.getSample(x - 1, y, 3) >= 250)))
		    dst.setDataElements(x, y, fcol);
	    }
	}
	return(ol);
    }
    
    public static BufferedImage outline2(BufferedImage img, Color col) {
	BufferedImage ol = outline(img, col);
	Graphics g = ol.getGraphics();
	g.drawImage(img, 1, 1, null);
	g.dispose();
	return(ol);
    }
    
    public static BufferedImage monochromize(BufferedImage img, Color col) {
	Coord sz = imgsz(img);
	BufferedImage ret = TexI.mkbuf(sz);
	int[] ob = new int[4];
	for(int y = 0; y < sz.y; y++) {
	    for(int x = 0; x < sz.x; x++) {
		int px = img.getRGB(x, y);
		int r = rgbm.getRed(px),
		    g = rgbm.getGreen(px),
		    b = rgbm.getBlue(px),
		    a = rgbm.getAlpha(px);
		int max = Math.max(r, Math.max(g, b)),
		    min = Math.min(r, Math.min(g, b));
		int val = (max + min) / 2;
		ob[0] = (col.getRed()   * val) / 255;
		ob[1] = (col.getGreen() * val) / 255;
		ob[2] = (col.getBlue()  * val) / 255;
		ob[3] = a;
		ret.setRGB(x, y, rgbm.getDataElement(ob, 0));
	    }
	}
	return(ret);
    }

    public static WritableRaster alpharaster(Coord sz) {
	return(Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, sz.x, sz.y, 1, null));
    }

    public static void imggrow(WritableRaster img, int rad) {
	int h = img.getHeight(), w = img.getWidth();
	int[] buf = new int[w * h];
	int o = 0;
	for(int y = 0; y < h; y++) {
	    for(int x = 0; x < w; x++) {
		int m = 0;
		int u = Math.max(0, y - rad), b = Math.min(h - 1, y + rad);
		int l = Math.max(0, x - rad), r = Math.min(w - 1, x + rad);
		for(int y2 = u; y2 <= b; y2++) {
		    for(int x2 = l; x2 <= r; x2++) {
			m = Math.max(m, img.getSample(x2, y2, 0));
		    }
		}
		buf[o++] = m;
	    }
	}
	img.setSamples(0, 0, w, h, 0, buf);
    }

    public static void imgblur(WritableRaster img, int rad, double var) {
	int h = img.getHeight(), w = img.getWidth();
	double[] gk = new double[(rad * 2) + 1];
	for(int i = 0; i <= rad; i++)
	    gk[rad + i] = gk[rad - i] = Math.exp(-0.5 * Math.pow(i / var, 2.0));
	double s = 0;
	for(double cw : gk) s += cw;
	s = 1.0 / s;
	for(int i = 0; i <= rad * 2; i++)
	    gk[i] *= s;
	int[] buf = new int[w * h];
	for(int band = 0; band < img.getNumBands(); band++) {
	    int o;
	    o = 0;
	    for(int y = 0; y < h; y++) {
		for(int x = 0; x < w; x++) {
		    double v = 0;
		    int l = Math.max(0, x - rad), r = Math.min(w - 1, x + rad);
		    for(int x2 = l, ks = l - (x - rad); x2 <= r; x2++, ks++)
			v += img.getSample(x2, y, band) * gk[ks];
		    buf[o++] = (int)v;
		}
	    }
	    img.setSamples(0, 0, w, h, band, buf);
	    o = 0;
	    for(int y = 0; y < h; y++) {
		for(int x = 0; x < w; x++) {
		    double v = 0;
		    int u = Math.max(0, y - rad), b = Math.min(h - 1, y + rad);
		    for(int y2 = u, ks = u - (y - rad); y2 <= b; y2++, ks++)
			v += img.getSample(x, y2, band) * gk[ks];
		    buf[o++] = (int)v;
		}
	    }
	    img.setSamples(0, 0, w, h, band, buf);
	}
    }

    public static void alphadraw(WritableRaster dst, Raster alpha, Coord ul, Color col) {
	int r = col.getRed(), g = col.getGreen(), b = col.getBlue(), ba = col.getAlpha();
	int w = alpha.getWidth(), h = alpha.getHeight();
	for(int y = 0; y < h; y++) {
	    for(int x = 0; x < w; x++) {
		int a = (alpha.getSample(x, y, 0) * ba) / 255;
		int dx = x + ul.x, dy = y + ul.y;
		dst.setSample(dx, dy, 0, ((r * a) + (dst.getSample(dx, dy, 0) * (255 - a))) / 255);
		dst.setSample(dx, dy, 1, ((g * a) + (dst.getSample(dx, dy, 1) * (255 - a))) / 255);
		dst.setSample(dx, dy, 2, ((b * a) + (dst.getSample(dx, dy, 2) * (255 - a))) / 255);
		dst.setSample(dx, dy, 3, Math.max((ba * a) / 255, dst.getSample(dx, dy, 3)));
	    }
	}
    }

    public static BufferedImage blurmask(BufferedImage img, int rad, Color col) {
	Coord bsz = imgsz(img), sz = bsz.add(rad * 4, rad * 4);
	WritableRaster mask = alpharaster(sz);
	mask.setSamples(rad * 2, rad * 2, bsz.x, bsz.y, 0, img.getRaster().getSamples(0, 0, bsz.x, bsz.y, 3, (int[])null));
	imggrow(mask, rad);
	imgblur(mask, rad, rad);
	BufferedImage out = TexI.mkbuf(sz);
	alphadraw(out.getRaster(), mask, Coord.z, col);
	return(out);
    }

    public static BufferedImage blurmask2(BufferedImage img, int rad, Color col) {
	BufferedImage mask = blurmask(img, rad, col);
	Graphics g = mask.getGraphics();
	g.drawImage(img, rad * 2, rad * 2, null);
	g.dispose();
	return(mask);
    }

    public static void dumpband(Raster img, int band) {
	int w = img.getWidth(), h = img.getHeight();
	for(int y = 0; y < h; y++) {
	    for(int x = 0; x < w; x++) {
		System.err.print((char)('a' + ((img.getSample(x, y, band) * ('z' - 'a')) / 255)));
	    }
	    System.err.println();
	}
    }

    public static int floordiv(int a, int b) {
	if(a < 0)
	    return(((a + 1) / b) - 1);
	else
	    return(a / b);
    }
    
    public static int floormod(int a, int b) {
	int r = a % b;
	if(r < 0)
	    r += b;
	return(r);
    }

    public static int floordiv(float a, float b) {
	return((int)Math.floor(a / b));
    }
    
    public static float floormod(float a, float b) {
	float r = a % b;
	if(r < 0)
	    r += b;
	return(r);
    }

    public static double clip(double d, double min, double max) {
	if(d < min)
	    return(min);
	if(d > max)
	    return(max);
	return(d);
    }
    
    public static float clip(float d, float min, float max) {
	if(d < min)
	    return(min);
	if(d > max)
	    return(max);
	return(d);
    }
    
    public static int clip(int i, int min, int max) {
	if(i < min)
	    return(min);
	if(i > max)
	    return(max);
	return(i);
    }
    
    public static Color blendcol(Color in, Color bl) {
	int f1 = bl.getAlpha();
	int f2 = 255 - bl.getAlpha();
	return(new Color(((in.getRed() * f2) + (bl.getRed() * f1)) / 255,
			 ((in.getGreen() * f2) + (bl.getGreen() * f1)) / 255,
			 ((in.getBlue() * f2) + (bl.getBlue() * f1)) / 255,
			 in.getAlpha()));
    }
    
    public static void serialize(Object obj, OutputStream out) throws IOException {
	ObjectOutputStream oout = new ObjectOutputStream(out);
	oout.writeObject(obj);
	oout.flush();
    }
    
    public static byte[] serialize(Object obj) {
	ByteArrayOutputStream out = new ByteArrayOutputStream();
	try {
	    serialize(obj, out);
	} catch(IOException e) {
	    throw(new RuntimeException(e));
	}
	return(out.toByteArray());
    }
    
    public static Object deserialize(InputStream in) throws IOException {
	ObjectInputStream oin = new ObjectInputStream(in);
	try {
	    return(oin.readObject());
	} catch(ClassNotFoundException e) {
	    return(null);
	}
    }
    
    public static Object deserialize(byte[] buf) {
	if(buf == null)
	    return(null);
	InputStream in = new ByteArrayInputStream(buf);
	try {
	    return(deserialize(in));
	} catch(IOException e) {
	    return(null);
	}
    }
    
    public static boolean parsebool(String s, boolean def) {
	if(s == null)
	    return(def);
	else if(s.equalsIgnoreCase("1") || s.equalsIgnoreCase("on") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes"))
	    return(true);
	else if(s.equalsIgnoreCase("0") || s.equalsIgnoreCase("off") || s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no"))
	    return(false);
	return(def);
    }
    
    /* Just in case anyone doubted that Java is stupid. :-/ */
    public static FloatBuffer bufcp(float[] a) {
	FloatBuffer b = mkfbuf(a.length);
	b.put(a);
	b.rewind();
	return(b);
    }
    public static ShortBuffer bufcp(short[] a) {
	ShortBuffer b = mksbuf(a.length);
	b.put(a);
	b.rewind();
	return(b);
    }
    public static FloatBuffer bufcp(FloatBuffer a) {
	a.rewind();
	FloatBuffer ret = mkfbuf(a.remaining());
	ret.put(a).rewind();
	return(ret);
    }
    public static IntBuffer bufcp(IntBuffer a) {
	a.rewind();
	IntBuffer ret = mkibuf(a.remaining());
	ret.put(a).rewind();
	return(ret);
    }
    public static ByteBuffer mkbbuf(int n) {
	return(ByteBuffer.allocateDirect(n));
    }
    public static FloatBuffer mkfbuf(int n) {
	return(ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer());
    }
    public static ShortBuffer mksbuf(int n) {
	return(ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder()).asShortBuffer());
    }
    public static IntBuffer mkibuf(int n) {
	return(ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asIntBuffer());
    }

    public static float[] c2fa(Color c) {
	return(new float[] {
		((float)c.getRed() / 255.0f),
		((float)c.getGreen() / 255.0f),
		((float)c.getBlue() / 255.0f),
		((float)c.getAlpha() / 255.0f)
	    });
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T[] mkarray(Class<T> cl, int len) {
	return((T[])Array.newInstance(cl, len));
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] splice(T[] src, int off, int len) {
	T[] dst = (T[])Array.newInstance(src.getClass().getComponentType(), len);
	System.arraycopy(src, off, dst, 0, len);
	return(dst);
    }
    
    public static <T> T[] splice(T[] src, int off) {
	return(splice(src, off, src.length - off));
    }
    
    public static <T> T el(Iterable<T> c) {
	return(c.iterator().next());
    }
    
    public static <T> T construct(Constructor<T> cons, Object... args) {
	try {
	    return(cons.newInstance(args));
	} catch(InstantiationException e) {
	    throw(new RuntimeException(e));
	} catch(IllegalAccessException e) {
	    throw(new RuntimeException(e));
	} catch(InvocationTargetException e) {
	    if(e.getCause() instanceof RuntimeException)
		throw((RuntimeException)e.getCause());
	    throw(new RuntimeException(e.getCause()));
	}
    }

    static {
	Console.setscmd("die", new Console.Command() {
		public void run(Console cons, String[] args) {
		    throw(new Error("Triggered death"));
		}
	    });
	Console.setscmd("threads", new Console.Command() {
		public void run(Console cons, String[] args) {
		    Utils.dumptg(null, cons.out);
		}
	    });
	Console.setscmd("gc", new Console.Command() {
		public void run(Console cons, String[] args) {
		    System.gc();
		}
	    });
    }
}
