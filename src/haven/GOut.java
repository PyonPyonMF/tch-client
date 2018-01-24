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

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.media.opengl.*;
import java.nio.*;
import haven.render.*;

public class GOut {
    public static final VertexArray.Layout vf_pos = new VertexArray.Layout(new VertexArray.Layout.Input(Ortho2D.pos, new VectorFormat(2, NumberFormat.FLOAT32), 0, 0, 8));
    public static final VertexArray.Layout vf_tex = new VertexArray.Layout(new VertexArray.Layout.Input(Ortho2D.pos, new VectorFormat(2, NumberFormat.FLOAT32), 0, 0, 16),
									   new VertexArray.Layout.Input(ColorTex.texc, new VectorFormat(2, NumberFormat.FLOAT32), 0, 8, 16));
    public final Render out;
    public Coord ul, br, tx;
    private final GOut root;
    private final Pipe def2d, cur2d;

    protected GOut(GOut o) {
	this.out = o.out;
	this.ul = o.ul;
	this.br = o.br;
	this.tx = o.tx;
	this.root = o.root;
	this.def2d = o.def2d;
	this.cur2d = def2d.copy();
    }

    public GOut(Render out, Pipe def2d, Coord sz) {
	this.out = out;
	this.ul = this.tx = Coord.z;
	this.br = sz;
	this.root = this;
	this.def2d = def2d;
	this.cur2d = def2d.copy();
    }

    public GOut root() {
	return(root);
    }

    public Pipe basicstate() {
	return(def2d.copy());
    }

    public Pipe state() {
	return(cur2d);
    }

    public void image(BufferedImage img, Coord c) {
	if(img == null)
	    return;
	Tex tex = new TexI(img);
	image(tex, c);
	tex.dispose();
    }

    public void image(Resource.Image img, Coord c) {
	if(img == null)
	    return;
	image(img.tex(), c.add(img.o));
    }

    /* Draw texture at c, quite simply. */
    public void image(Tex tex, Coord c) {
	if(tex == null)
	    return;
	tex.crender(this, c.add(tx), ul, br);
    }

    public void image(Indir<Tex> tex, Coord c) {
	image(tex.get(), c);
    }

    public void aimage(Tex tex, Coord c, double ax, double ay) {
	Coord sz = tex.sz();
	image(tex, c.add((int)((double)sz.x * -ax), (int)((double)sz.y * -ay)));
    }

    /* Draw texture at c, scaling it to sz. */
    public void image(Tex tex, Coord c, Coord sz) {
	if(tex == null)
	    return;
	tex.crender(this, c.add(tx), sz, ul, br);
    }

    /* Draw texture at c, clipping everything outside [ul, br). */
    public void image(Tex tex, Coord c, Coord ul, Coord br) {
	if(tex == null)
	    return;
	ul = ul.add(this.tx);
	br = br.add(this.tx);
	if(ul.x < this.ul.x)
	    ul.x = this.ul.x;
	if(ul.y < this.ul.y)
	    ul.y = this.ul.y;
	if(br.x > this.br.x)
	    br.x = this.br.x;
	if(br.y > this.br.y)
	    br.y = this.br.y;
	tex.crender(this, c.add(this.tx), ul, br);
    }

    public void rimagev(Tex tex, Coord c, int h) {
	Coord cc = new Coord(c);
	Coord sz = new Coord(tex.sz().x, h);
	for(; cc.y < c.y + h; cc.y += tex.sz().y)
	    image(tex, cc, c, sz);
    }

    public void rimageh(Tex tex, Coord c, int w) {
	Coord cc = new Coord(c);
	Coord sz = new Coord(w, tex.sz().y);
	for(; cc.x < c.x + w; cc.x += tex.sz().x)
	    image(tex, cc, c, sz);
    }

    public void rimage(Tex tex, Coord c, Coord sz) {
	Coord cc = new Coord();
	for(cc.y = c.y; cc.y < c.y + sz.y; cc.y += tex.sz().y) {
	    for(cc.x = c.x; cc.x < c.x + sz.x; cc.x += tex.sz().x)
		image(tex, cc, c, sz);
	}
    }

    /* Draw texture at c, with the extra state s applied. */
    public void image(Tex tex, Coord c, State s) {
	Pipe bk = cur2d.copy();
	cur2d.prep(s);
	tex.crender(this, c.add(tx), ul, br);
	cur2d.copy(bk);
    }

    public void atext(String text, Coord c, double ax, double ay) {
	Text t = Text.render(text);
	Tex T = t.tex();
	aimage(T, c, ax, ay);
	T.dispose();
    }

    public void text(String text, Coord c) {
	atext(text, c, 0, 0);
    }

    public void drawp(Model.Mode mode, float[] data, int n) {
	out.draw(cur2d, new Model(mode, new VertexArray(vf_pos, n, new VertexArray.Buffer(data.length * 4, DataBuffer.Usage.EPHEMERAL, DataBuffer.Filler.of(data))), null));
    }

    public void drawp(Model.Mode mode, float[] data) {
	drawp(mode, data, data.length / 2);
    }

    public void drawt(Model.Mode mode, float[] data, int n) {
	out.draw(cur2d, new Model(mode, new VertexArray(vf_tex, n, new VertexArray.Buffer(data.length * 4, DataBuffer.Usage.EPHEMERAL, DataBuffer.Filler.of(data))), null));
    }

    public void drawt(Model.Mode mode, float[] data) {
	drawt(mode, data, data.length / 4);
    }

    public void line(Coord c1, Coord c2, double w) {
	/* XXXRENDER: gl.glLineWidth((float)w); */
	float[] data = {c1.x + tx.x, c1.y + tx.y, c2.x + tx.x, c2.y + tx.y};
	drawp(Model.Mode.LINES, data);
    }

    public void frect2(Coord ul, Coord br) {
	ul = new Coord(Math.max(ul.x + tx.x, this.ul.x), Math.max(ul.y + tx.y, this.ul.y));
	br = new Coord(Math.min(br.x + tx.x, this.br.x), Math.min(br.y + tx.y, this.br.y));
	if((ul.x >= br.x) || (ul.y >= br.y))
	    return;
	float[] data = {br.x, ul.y, br.x, br.y, ul.x, ul.y, ul.x, br.y};
	drawp(Model.Mode.TRIANGLE_STRIP, data);
    }

    public void frect(Coord ul, Coord sz) {
	frect2(ul, ul.add(sz));
    }

    /* XXXRENDER
    public void ftexrect(Coord ul, Coord sz, GLState s, float tl, float tt, float tr, float tb) {
	ul = tx.add(ul);
	Coord br = ul.add(sz);
	Coord ult = new Coord(0, 0);
	Coord brt = new Coord(sz);
	if(ul.x < this.ul.x) {
	    ult.x += this.ul.x - ul.x;
	    ul.x = this.ul.x;
	}
	if(ul.y < this.ul.y) {
	    ult.y += this.ul.y - ul.y;
	    ul.y = this.ul.y;
	}
	if(br.x > this.ul.x + this.sz.x) {
	    brt.x -= br.x - (this.ul.x + this.sz.x);
	    br.x = this.ul.x + this.sz.x;
	}
	if(br.y > this.ul.y + this.sz.y) {
	    brt.y -= br.y - (this.ul.y + this.sz.y);
	    br.y = this.ul.y + this.sz.y;
	}
	if((ul.x >= br.x) || (ul.y >= br.y))
	    return;

	st.set(cur2d);
	state(s);
	apply();

	float l = tl + ((tr - tl) * ((float)ult.x) / ((float)sz.x));
	float t = tt + ((tb - tt) * ((float)ult.y) / ((float)sz.y));
	float r = tl + ((tr - tl) * ((float)brt.x) / ((float)sz.x));
	float b = tt + ((tb - tt) * ((float)brt.y) / ((float)sz.y));
	gl.glBegin(GL2.GL_QUADS);
	gl.glTexCoord2f(l, b); gl.glVertex2i(ul.x, ul.y);
	gl.glTexCoord2f(r, b); gl.glVertex2i(br.x, ul.y);
	gl.glTexCoord2f(r, t); gl.glVertex2i(br.x, br.y);
	gl.glTexCoord2f(l, t); gl.glVertex2i(ul.x, br.y);
	gl.glEnd();
	checkerr();
    }

    public void ftexrect(Coord ul, Coord sz, GLState s) {
	ftexrect(ul, sz, s, 0, 0, 1, 1);
    }
    */

    public void fellipse(Coord c, Coord r, double a1, double a2) {
	if(a2 >= a1)
	    return;
	c = c.add(tx);
	double d = 0.1;
	int n = (int)Math.floor((a2 - a1) / d);
	float[] data = new float[(n + 3) * 2];
	int p = 0;
	data[p++] = c.x; data[p++] = c.y;
	for(int i = 0; i <= n; i++) {
	    data[p++] = (float)(c.x + (Math.cos(a1 + (i * d)) * r.x));
	    data[p++] = (float)(c.y - (Math.sin(a1 + (i * d)) * r.y));
	}
	data[p++] = (float)(c.x + (Math.cos(a2) * r.x));
	data[p++] = (float)(c.y - (Math.sin(a2) * r.y));
	drawp(Model.Mode.TRIANGLE_FAN, data);
    }
	
    public void fellipse(Coord c, Coord r) {
	fellipse(c, r, 0, Math.PI * 2);
    }

    public void rect2(Coord ul, Coord br) {
	ul = ul.add(tx); br = br.add(tx);
	float h = 0.5f;
	float[] data = {ul.x + h, ul.y + h,
			br.x + h, ul.y + h,
			br.x + h, br.y + h,
			ul.x + h, br.y + h,
			ul.x + h, br.y + h};
	drawp(Model.Mode.LINE_STRIP, data);
    }

    public void rect(Coord ul, Coord sz) {
	rect(ul, ul.add(sz).sub(1, 1));
    }

    public void prect(Coord c, Coord ul, Coord br, double a) {
	/* XXXRENDER: gl.glEnable(GL2.GL_POLYGON_SMOOTH); */
	c = c.add(tx);
	float[] data = new float[14];
	int p = 0;
	data[p++] = c.x; data[p++] = c.y;
	data[p++] = c.x; data[p++] = c.y + ul.y;
	double p2 = Math.PI / 2;
	all: {
	    float tc;

	    tc = (float)(Math.tan(a) * -ul.y);
	    if((a > p2) || (tc > br.x)) {
		data[p++] = c.x + br.x; data[p++] = c.y + ul.y;
	    } else {
		data[p++] = c.x + tc; data[p++] = c.y + ul.y;
		break all;
	    }

	    tc = (float)(Math.tan(a - (Math.PI / 2)) * br.x);
	    if((a > p2 * 2) || (tc > br.y)) {
		data[p++] = c.x + br.x; data[p++] = c.y + br.y;
	    } else {
		data[p++] = c.x + br.x; data[p++] = c.y + tc;
		break all;
	    }

	    tc = (float)(-Math.tan(a - Math.PI) * br.y);
	    if((a > p2 * 3) || (tc < ul.x)) {
		data[p++] = c.x + ul.x; data[p++] = c.y + br.y;
	    } else {
		data[p++] = c.x + tc; data[p++] = c.y + br.y;
		break all;
	    }

	    tc = (float)(-Math.tan(a - (3 * Math.PI / 2)) * -ul.x);
	    if((a > p2 * 4) || (tc < ul.y)) {
		data[p++] = c.x + ul.x; data[p++] = c.y + ul.y;
	    } else {
		data[p++] = c.x + ul.x; data[p++] = c.y + tc;
		break all;
	    }

	    tc = (float)(Math.tan(a) * -ul.y);
	    data[p++] = c.x + tc; data[p++] = c.y + ul.y;
	}
	drawp(Model.Mode.TRIANGLE_FAN, data, p / 2);
    }

    public <T extends State> T curstate(State.Slot<T> slot) {
	return(cur2d.get(slot));
    }

    public void usestate(State st) {
	st.apply(cur2d);
    }

    public <T extends State> void usestate(State.Slot<? super T> slot) {
	cur2d.put(slot, null);
    }

    public void defstate() {
	cur2d.copy(def2d);
    }

    public void chcolor(Color c) {
	usestate(new BaseColor(c));
    }

    public void chcolor(int r, int g, int b, int a) {
	chcolor(Utils.clipcol(r, g, b, a));
    }

    public void chcolor() {
	usestate(BaseColor.slot);
    }

    public Color getcolor() {
	BaseColor color = curstate(BaseColor.slot);
	return((color == null)?Color.WHITE:color.color());
    }

    public GOut reclip2(Coord ul, Coord br) {
	GOut g = new GOut(this);
	g.ul = this.tx.add(ul);
	g.br = this.tx.add(br);
	g.tx = new Coord(g.ul);
	g.ul.x = Math.max(g.ul.x, this.ul.x);
	g.ul.y = Math.max(g.ul.y, this.ul.y);
	g.br.x = Math.min(g.br.x, this.br.x);
	g.br.y = Math.min(g.br.y, this.br.y);
	return(g);
    }

    public GOut reclip(Coord ul, Coord sz) {
	return(reclip2(ul, ul.add(sz)));
    }

    public GOut reclipl2(Coord ul, Coord br) {
	GOut g = new GOut(this);
	g.ul = this.tx.add(ul);
	g.br = this.tx.add(br);
	g.tx = new Coord(g.ul);
	return(g);
    }

    public GOut reclipl(Coord ul, Coord sz) {
	return(reclipl2(ul, ul.add(sz)));
    }

    /* XXXRENDER 
    public void getpixel(final Coord c, final Callback<Color> cb) {
	gl.bglSubmit(new BGL.Request() {
		public void run(GL2 gl) {
		    byte[] buf = new byte[4];
		    gl.glReadPixels(c.x + tx.x, root.sz.y - c.y - tx.y, 1, 1, GL.GL_RGBA, GL2.GL_UNSIGNED_BYTE, ByteBuffer.wrap(buf));
		    Color result = new Color(((int)buf[0]) & 0xff, ((int)buf[1]) & 0xff, ((int)buf[2]) & 0xff, ((int)buf[3]) & 0xff);
		    checkerr(gl);
		    cb.done(result);
		}
	    });
    }
    
    public void getimage(final Coord ul, final Coord sz, final Callback<BufferedImage> cb) {
	gl.bglSubmit(new BGL.Request() {
		public void run(GL2 gl) {
		    byte[] buf = new byte[sz.x * sz.y * 4];
		    gl.glReadPixels(ul.x + tx.x, root.sz.y - ul.y - sz.y - tx.y, sz.x, sz.y, GL.GL_RGBA, GL2.GL_UNSIGNED_BYTE, ByteBuffer.wrap(buf));
		    checkerr(gl);
		    for(int y = 0; y < sz.y / 2; y++) {
			int to = y * sz.x * 4, bo = (sz.y - y - 1) * sz.x * 4;
			for(int o = 0; o < sz.x * 4; o++, to++, bo++) {
			    byte t = buf[to];
			    buf[to] = buf[bo];
			    buf[bo] = t;
			}
		    }
		    WritableRaster raster = Raster.createInterleavedRaster(new DataBufferByte(buf, buf.length), sz.x, sz.y, 4 * sz.x, 4, new int[] {0, 1, 2, 3}, null);
		    cb.done(new BufferedImage(TexI.glcm, raster, false, null));
		}
	    });
    }

    public void getimage(final Callback<BufferedImage> cb) {
	getimage(Coord.z, sz, cb);
    }
    */
}
