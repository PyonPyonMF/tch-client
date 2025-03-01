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

import java.awt.*;
import java.util.*;
import java.util.List;

import java.util.function.*;
import java.awt.Color;
import java.util.stream.Collectors;

import haven.MapFile.Segment;
import haven.MapFile.DataGrid;
import haven.MapFile.GridInfo;
import me.ender.minimap.*;

import static haven.MCache.cmaps;
import static haven.MCache.tilesz;
import static haven.OCache.posres;

public class MiniMap extends Widget {
    public static final Tex bg = Resource.loadtex("gfx/hud/mmap/ptex");
    public static final Tex nomap = Resource.loadtex("gfx/hud/mmap/nomap");
    public static final Tex plp = ((TexI)Resource.loadtex("gfx/hud/mmap/plp")).filter(haven.render.Texture.Filter.LINEAR);
    private static final Color BIOME_BG = new Color(0, 0, 0, 80);
    public final MapFile file;
    public Location curloc;
    private static KinInfo kin;
    public Location sessloc;
    public GobIcon.Settings iconconf;
    public List<DisplayIcon> icons = Collections.emptyList();
    protected Locator setloc;
    protected boolean follow;
    protected int zoomlevel = 0;
    protected DisplayGrid[] display;
    private final Map<Long, Tex> namemap = new HashMap<>(50);
    protected Area dgext, dtext;
    protected Segment dseg;
    protected int dlvl;
    protected Location dloc;
    private String biome;
    private Tex biometex;
    public boolean big = false;
    public int scale = 1;

    public MiniMap(Coord sz, MapFile file) {
	super(sz);
	this.file = file;
    }

    public MiniMap(MapFile file) {
	this(Coord.z, file);
    }

    protected void attached() {
	if(iconconf == null) {
	    GameUI gui = getparent(GameUI.class);
	    if(gui != null)
		iconconf = gui.iconconf;
	}
	super.attached();
    }

    public static class Location {
	public final Segment seg;
	public final Coord tc;

	public Location(Segment seg, Coord tc) {
	    Objects.requireNonNull(seg);
	    Objects.requireNonNull(tc);
	    this.seg = seg; this.tc = tc;
	}
    }

    public interface Locator {
	Location locate(MapFile file) throws Loading;
    }

    public static class SessionLocator implements Locator {
	public final Session sess;
	private MCache.Grid lastgrid = null;
	private Location lastloc;

	public SessionLocator(Session sess) {this.sess = sess;}

	public Location locate(MapFile file) {
	    MCache map = sess.glob.map;
	    if(lastgrid != null) {
		synchronized(map.grids) {
		    if(map.grids.get(lastgrid.gc) == lastgrid)
			return(lastloc);
		}
		lastgrid = null;
		lastloc = null;
	    }
	    Collection<MCache.Grid> grids = new ArrayList<>();
	    synchronized(map.grids) {
		grids.addAll(map.grids.values());
	    }
	    for(MCache.Grid grid : grids) {
		GridInfo info = file.gridinfo.get(grid.id);
		if(info == null)
		    continue;
		Segment seg = file.segments.get(info.seg);
		if(seg != null) {
		    Location ret = new Location(seg, info.sc.sub(grid.gc).mul(cmaps));
		    lastgrid = grid;
		    lastloc = ret;
		    return(ret);
		}
	    }
	    throw(new Loading("No mapped grids found."));
	}
    }

    public static class MapLocator implements Locator {
	public final MapView mv;

	public MapLocator(MapView mv) {this.mv = mv;}

	public Location locate(MapFile file) {
	    Coord mc = new Coord2d(mv.getcc()).floor(MCache.tilesz);
	    if(mc == null)
		throw(new Loading("Waiting for initial location"));
	    MCache.Grid plg = mv.ui.sess.glob.map.getgrid(mc.div(cmaps));
	    GridInfo info = file.gridinfo.get(plg.id);
	    if(info == null)
		throw(new Loading("No grid info, probably coming soon"));
	    Segment seg = file.segments.get(info.seg);
	    if(seg == null)
		throw(new Loading("No segment info, probably coming soon"));
	    return(new Location(seg, info.sc.mul(cmaps).add(mc.sub(plg.ul))));
	}
    }

    public static class SpecLocator implements Locator {
	public final long seg;
	public final Coord tc;

	public SpecLocator(long seg, Coord tc) {this.seg = seg; this.tc = tc;}

	public Location locate(MapFile file) {
	    Segment seg = file.segments.get(this.seg);
	    if(seg == null)
		return(null);
	    return(new Location(seg, tc));
	}
    }

    public void center(Location loc) {
	curloc = loc;
    }

    public Location resolve(Locator loc) {
	if(!file.lock.readLock().tryLock())
	    throw(new Loading("Map file is busy"));
	try {
	    return(loc.locate(file));
	} finally {
	    file.lock.readLock().unlock();
	}
    }

    public Coord xlate(Location loc) {
	Location dloc = this.dloc;
	if((dloc == null) || (dloc.seg != loc.seg))
	    return(null);
	return(loc.tc.sub(dloc.tc).div(scalef()).add(sz.div(2)));
    }

    public Location xlate(Coord sc) {
	Location dloc = this.dloc;
	if(dloc == null)
	    return(null);
	Coord tc = sc.sub(sz.div(2)).mul(scalef()).add(dloc.tc);
	return(new Location(dloc.seg, tc));
    }

    private Locator sesslocator;
    public void tick(double dt) {
	if(setloc != null) {
	    try {
		Location loc = resolve(setloc);
		center(loc);
		if(!follow)
		    setloc = null;
	    } catch(Loading l) {
	    }
	}
	if((sesslocator == null) && (ui != null) && (ui.sess != null))
	    sesslocator = new SessionLocator(ui.sess);
	if(sesslocator != null) {
	    try {
		sessloc = resolve(sesslocator);
	    } catch(Loading l) {
	    }
	}
	icons = findicons(icons);
	resolveNames();
	if(CFG.MMAP_SHOW_BIOMES.get()) {
	    Coord mc = rootxlate(ui.mc);
	    if(mc.isect(Coord.z, sz)) {
		setBiome(xlate(mc));
	    } else {
		setBiome(null);
	    }
	}
    }

    public void center(Locator loc) {
	setloc = loc;
	follow = false;
    }

    public void follow(Locator loc) {
	setloc = loc;
	follow = true;
    }

    public class DisplayIcon {
	public final GobIcon icon;
	public final Gob gob;
	public final GobIcon.Image img;
	public Coord2d rc = null;
	public Coord sc = null;
	public double ang = 0.0;
	public Color col = Color.WHITE;
	public int z;
	public double stime;

	public DisplayIcon(GobIcon icon) {
	    this.icon = icon;
	    this.gob = icon.gob;
	    this.img = icon.img();
	    this.z = this.img.z;
	    this.stime = Utils.rtime();
	}

	public void update(Coord2d rc, double ang) {
	    this.rc = rc;
	    this.ang = ang;
	}

	public void dispupdate() {
	    if((this.rc == null) || (sessloc == null) || (dloc == null) || (dloc.seg != sessloc.seg))
		this.sc = null;
	    else
		this.sc = p2c(this.rc);
	}
    
	public Object tooltip() {
	    KinInfo kin = kin();
	    if(kin != null) {
		if(kin.isVillager() && kin.name.trim().isEmpty()) {
		    return "Villager";
		} else {
		    return kin.rendered();
		}
	    }
	    return icon.tooltip();
	}
    
	public KinInfo kin() {
	    return icon.gob.getattr(KinInfo.class);
	}
    
	public boolean isPlayer() {
	    return "gfx/hud/mmap/plo".equals(icon.res.get().name);
	}
	
	public boolean isDead() {
	    return gob.anyOf(GobTag.DEAD, GobTag.KO);
	}
    }

    public static class MarkerID extends GAttrib {
	public final long id;

	public MarkerID(Gob gob, long id) {
	    super(gob);
	    this.id = id;
	}

	public static Gob find(OCache oc, long id) {
	    synchronized(oc) {
		for(Gob gob : oc) {
		    MarkerID iattr = gob.getattr(MarkerID.class);
		    if((iattr != null) && (iattr.id == id))
			return(gob);
		}
	    }
	    return(null);
	}
    }

    public static class DisplayMarker {
	public static final Resource.Image flagbg, flagfg;
	public static final Coord flagcc;
	public final Marker m;
	public Text tip;
	public Area hit;
	private Resource.Image img;
	private Coord imgsz;
	private Coord cc;

	static {
	    Resource flag = Resource.local().loadwait("gfx/hud/mmap/flag");
	    flagbg = flag.layer(Resource.imgc, 1);
	    flagfg = flag.layer(Resource.imgc, 0);
	    flagcc = UI.scale(flag.layer(Resource.negc).cc);
	}

	public DisplayMarker(Marker marker, final UI ui) {
	    this.m = marker;
	    checkTip(marker.tip(ui));
	    if(marker instanceof PMarker)
		this.hit = Area.sized(flagcc.inv(), UI.scale(flagbg.sz));
	}

	public void draw(GOut g, Coord c, final float scale, final UI ui, final MapFile file, final boolean canShowName) {
	    if(Config.always_true) {
		checkTip(m.tip(ui));
		if(visible()) {m.draw(g, c, canShowName ? tip : null, scale, file);}
		return;
	    }
	    if(m instanceof PMarker) {
		Coord ul = c.sub(flagcc);
		g.chcolor(((PMarker)m).color);
		g.image(flagfg, ul);
		g.chcolor();
		g.image(flagbg, ul);
	    } else if(m instanceof SMarker) {
		SMarker sm = (SMarker)m;
		try {
		    if(cc == null) {
			Resource res = sm.res.loadsaved(Resource.remote());
			img = res.layer(Resource.imgc);
			Resource.Neg neg = res.layer(Resource.negc);
			cc = (neg != null) ? neg.cc : img.ssz.div(2);
			if(hit == null)
			    hit = Area.sized(cc.inv(), img.ssz);
		    }
		} catch(Loading l) {
		} catch(Exception e) {
		    cc = Coord.z;
		}
		if(img != null)
		    g.image(img, c.sub(cc));
	    }
	}
	
	private void checkTip(final String nm) {
	    if (tip == null || !tip.text.equals(nm)) {
		tip = Text.renderstroked(nm, Color.WHITE, Color.BLACK);
	    }
	}
	
	private Area hit(final UI ui) {
	    if (visible()) {
		if(hit == null)
		    hit = m.area();
		return hit;
	    } else {
		return null;
	    }
	}
    
	private boolean visible() {
	    return true;
	}
    }

    public static class DisplayGrid {
	public final MapFile file;
	public final Segment seg;
	public final Coord sc;
	public final Area mapext;
	public final Indir<? extends DataGrid> gref;
	private DataGrid cgrid = null;
	private Tex img = null;
	private Defer.Future<Tex> nextimg = null;

	public DisplayGrid(Segment seg, Coord sc, int lvl, Indir<? extends DataGrid> gref) {
	    this.file = seg.file();
	    this.seg = seg;
	    this.sc = sc;
	    this.gref = gref;
	    mapext = Area.sized(sc.mul(cmaps.mul(1 << lvl)), cmaps.mul(1 << lvl));
	}

	class CachedImage {
	    final Function<DataGrid, Defer.Future<Tex>> src;
	    DataGrid cgrid;
	    Defer.Future<Tex> next;
	    Tex img;

	    CachedImage(Function<DataGrid, Defer.Future<Tex>> src) {
		this.src = src;
	    }

	    public Tex get() {
		DataGrid grid = gref.get();
		if(grid != cgrid || !valid()) {
		    if(next != null)
			next.cancel();
		    next = getNext(grid);
		    cgrid = grid;
		}
		if(next != null) {
		    try {
			img = next.get();
		    } catch(Loading l) {}
		}
		return(img);
	    }
	    
	    protected Defer.Future<Tex> getNext(DataGrid grid) {
		return src.apply(grid);
	    }
	    
	    protected boolean valid() {return true;}
	}
    
	class CachedTileOverlay extends MiniMap.DisplayGrid.CachedImage {
	    private long seq = 0;
	    CachedTileOverlay(Function<MapFile.DataGrid, Defer.Future<Tex>> src) {
		super(src);
	    }
	    
	    @Override
	    protected boolean valid() {
		return this.seq == TileHighlight.seq;
	    }
	    
	    @Override
	    protected Defer.Future<Tex> getNext(DataGrid grid) {
	        this.seq = TileHighlight.seq;
		return super.getNext(grid);
	    }
	}

	private CachedImage img_c;
	public Tex img() {
	    if(img_c == null) {
		img_c = new CachedImage(grid -> {
			if(grid instanceof MapFile.ZoomGrid) {
			    return(Defer.later(() -> new TexI(grid.render(sc.mul(cmaps)))));
			} else {
			    return(Defer.later(new Defer.Callable<Tex>() {
				    MapFile.View view = new MapFile.View(seg);

				    public TexI call() {
					try(Locked lk = new Locked(file.lock.readLock())) {
					    for(int y = -1; y <= 1; y++) {
						for(int x = -1; x <= 1; x++) {
						    view.addgrid(sc.add(x, y));
						}
					    }
					    view.fin();
					    return(new TexI(MapSource.drawmap(view, Area.sized(sc.mul(cmaps), cmaps))));
					}
				    }
				}));
			}
		});
	    }
	    return(img_c.get());
	}

	private final Map<String, CachedImage> olimg_c = new HashMap<>();
	public Tex olimg(String tag) {
	    CachedImage ret;
	    synchronized(olimg_c) {
		if((ret = olimg_c.get(tag)) == null)
		    olimg_c.put(tag, ret = new CachedImage(grid -> Defer.later(() -> new TexI(grid.olrender(sc.mul(cmaps), tag)))));
	    }
	    return(ret.get());
	}
    
	public Tex tileimg() {
	    CachedImage ret;
	    synchronized(olimg_c) {
		if((ret = olimg_c.get(TileHighlight.TAG)) == null)
		    olimg_c.put(TileHighlight.TAG, ret = new CachedTileOverlay(grid -> Defer.later(() -> new TexI(TileHighlight.olrender(grid)))));
	    }
	    return(ret.get());
	}

	private Collection<DisplayMarker> markers = Collections.emptyList();
	private int markerseq = -1;
	public Collection<DisplayMarker> markers(boolean remark, final UI ui) {
	    if(remark && (markerseq != file.markerseq)) {
		if(file.lock.readLock().tryLock()) {
		    try {
			ArrayList<DisplayMarker> marks = new ArrayList<>();
			for(Marker mark : file.markers) {
			    if((mark.seg == this.seg.id) && mapext.contains(mark.tc))
				marks.add(new DisplayMarker(mark, ui));
			}
			marks.trimToSize();
			markers = (marks.size() == 0) ? Collections.emptyList() : marks;
			markerseq = file.markerseq;
		    } finally {
			file.lock.readLock().unlock();
		    }
		}
	    }
	    return(markers);
	}
    }

    private float scalef() {
	return(UI.unscale((float)(1 << dlvl)) / scale);
    }

    public Coord st2c(Coord tc) {
	return(UI.scale(tc.add(sessloc.tc).sub(dloc.tc).div(1 << dlvl)).mul(scale).add(sz.div(2)));
    }

    public Coord p2c(Coord2d pc) {
	return(st2c(pc.floor(tilesz)));
    }

    private void redisplay(Location loc) {
	Coord hsz = sz.div(2);
	Coord zmaps = cmaps.mul(1 << zoomlevel);
	Area next = Area.sized(loc.tc.sub(hsz.mul(UI.unscale((float)(1 << zoomlevel)))).div(zmaps),
	    UI.unscale(sz).div(cmaps).add(2, 2));
	if((display == null) || (loc.seg != dseg) || (zoomlevel != dlvl) || !next.equals(dgext)) {
	    DisplayGrid[] nd = new DisplayGrid[next.rsz()];
	    if((display != null) && (loc.seg == dseg) && (zoomlevel == dlvl)) {
		for(Coord c : dgext) {
		    if(next.contains(c))
			nd[next.ri(c)] = display[dgext.ri(c)];
		}
	    }
	    display = nd;
	    dseg = loc.seg;
	    dlvl = zoomlevel;
	    dgext = next;
	    dtext = Area.sized(next.ul.mul(zmaps), next.sz().mul(zmaps));
	}
	dloc = loc;
	if(file.lock.readLock().tryLock()) {
	    try {
		for(Coord c : dgext) {
		    if(display[dgext.ri(c)] == null)
			display[dgext.ri(c)] = new DisplayGrid(dloc.seg, c, dlvl, dloc.seg.grid(dlvl, c.mul(1 << dlvl)));
		}
	    } finally {
		file.lock.readLock().unlock();
	    }
	}
	for(DisplayIcon icon : icons)
	    icon.dispupdate();
    }

    public void drawgrid(GOut g, Coord ul, DisplayGrid disp) {
	try {
	    Tex img = disp.img();
	    if(img != null)
		g.image(img, ul, UI.scale(img.sz()).mul(scale));
	} catch(Loading l) {
	}
    }

    public void drawmap(GOut g) {
	Coord hsz = sz.div(2);
	for(Coord c : dgext) {
	    Coord ul = UI.scale(c.mul(cmaps).mul(scale)).sub(dloc.tc.div(scalef())).add(hsz);
	    DisplayGrid disp = display[dgext.ri(c)];
	    if(disp == null)
		continue;
	    drawgrid(g, ul, disp);
	}
    }

    public void drawmarkers(GOut g) {
	Coord hsz = sz.div(2);
	for(Coord c : dgext) {
	    DisplayGrid dgrid = display[dgext.ri(c)];
	    if(dgrid == null)
		continue;
	    for(DisplayMarker mark : dgrid.markers(true, ui)) {
		if(filter(mark))
		    continue;
		mark.draw(g, mark.m.tc.sub(dloc.tc).div(scalef()).add(hsz), scale, ui, file, big);
	    }
	}
    }

    public List<DisplayIcon> findicons(Collection<? extends DisplayIcon> prev) {
	if((ui.sess == null) || (iconconf == null))
	    return(Collections.emptyList());
	Map<Gob, DisplayIcon> pmap = Collections.emptyMap();
	if(prev != null) {
	    pmap = new HashMap<>();
	    for(DisplayIcon disp : prev)
		pmap.put(disp.gob, disp);
	}
	List<DisplayIcon> ret = new ArrayList<>();
	OCache oc = ui.sess.glob.oc;
	synchronized(oc) {
	    for(Gob gob : oc) {
		try {
		    GobIcon icon = gob.getattr(GobIcon.class);
		    if(icon != null) {
			GobIcon.Setting conf = iconconf.get(icon.res.get());
			if((conf != null) && conf.show && GobIconSettings.GobCategory.categorize(conf).enabled()) {
			    DisplayIcon disp = pmap.get(gob);
			    if(disp == null)
				disp = new DisplayIcon(icon);
			    disp.update(gob.rc, gob.a);
			    KinInfo kin = gob.getattr(KinInfo.class);
			    if((kin != null) && (kin.group < BuddyWnd.gc.length))
				disp.col = BuddyWnd.gc[kin.group];
			    ret.add(disp);
			}
		    }
		} catch(Loading l) {}
	    }
	}
	Collections.sort(ret, (a, b) -> a.z - b.z);
	if(ret.size() == 0)
	    return(Collections.emptyList());
	return(ret);
    }

    public void drawicons(GOut g) {
	if((sessloc == null) || (dloc.seg != sessloc.seg))
	    return;
	for(DisplayIcon disp : icons) {
	    if((disp.sc == null) || filter(disp))
		continue;
	    GobIcon.Image img = disp.img;
	    if(disp.isPlayer()) {
		g.chcolor(disp.kin() != null ? Color.WHITE : Color.RED);
		g.aimage(RadarCFG.Symbols.$circle.tex, disp.sc, 0.5, 0.5);
	    } else if (disp.isDead()) {
	        img = disp.icon.imggray();
	    }
	    
	    if(disp.col != null)
		g.chcolor(disp.col);
	    else
		g.chcolor();
	    if(!img.rot)
		g.image(img.tex, disp.sc.sub(img.cc));
	    else
		g.rotimage(img.tex, disp.sc, img.cc, -disp.ang + img.ao);
	}
	g.chcolor();
    }

    public void remparty() {
	Set<Gob> memb = new HashSet<>();
	synchronized(ui.sess.glob.party.memb) {
	    for(Party.Member m : ui.sess.glob.party.memb.values()) {
		Gob gob = m.getgob();
		if(gob != null)
		    memb.add(gob);
	    }
	}
	for(Iterator<DisplayIcon> it = icons.iterator(); it.hasNext();) {
	    DisplayIcon icon = it.next();
	    if(memb.contains(icon.gob))
		it.remove();
	}
    }
    
    public void resolveNames() {//used to load name textures even while the map is closed
	try {
	    synchronized (ui.sess.glob.party) {
		for (Party.Member m : ui.sess.glob.party.memb.values()) {
		    Coord2d ppc = m.getc();
		    if (ppc == null) // chars are located in different worlds
			continue;
		    if (ui.sess.glob.party.memb.size() == 1) //don't do anything if you don't have a party
			continue;
		    Gob gob = m.getgob();
		    if (gob != null) {
			KinInfo kin = gob.getattr(KinInfo.class);
			Tex tex = namemap.get(m.gobid);
			if (tex == null && kin != null && !gob.isMe()) { //if we don't already have this nametex in memory, set one up.
			    tex = Text.renderstroked(kin.name, Color.WHITE, Color.BLACK, Text.std).tex();
			    namemap.put(m.gobid, tex);
			}
		    }
		}
	    }
	} catch (Loading l) {
	    //Fail silently
	}
    }
    public void drawparty(GOut g) {
	synchronized(ui.sess.glob.party.memb) {
	    for(Party.Member m : ui.sess.glob.party.memb.values()) {
		try {
		    Coord2d ppc = m.getc();
		    Tex nametex = namemap.get(m.gobid);
		    if(ppc == null)
			continue;
		    Gob gob = m.getgob();
//		    if (gob != null && null != kin) {
//			kin = gob.getattr(KinInfo.class);
//			tex = namemap.get(kin.name);
//			if (tex == null && kin != null) {
//			    tex = Text.renderstroked(kin.name, Color.WHITE, Color.BLACK, Text.std).tex();
//			    namemap.put(kin.name, tex);
//			}
//		    }
		    if (nametex != null && gob == null) {
			    g.chcolor(Color.WHITE);
			    g.image(nametex, p2c(ppc).add(new Coord(-5, 5)));
//			    g.chcolor(Color.WHITE);
//			    g.aimage(RadarCFG.Symbols.$circle.tex, p2c(ppc), 0.5, 0.5);
//			    g.chcolor(m.col.getRed(), m.col.getGreen(), m.col.getBlue(), 255);
//			    g.rotimage(plp, p2c(ppc), plp.sz().div(2), -m.geta() - (Math.PI / 2));
//			    g.chcolor();
			}
		    g.chcolor(Color.WHITE);
		    g.aimage(RadarCFG.Symbols.$circle.tex, p2c(ppc), 0.5, 0.5);
		    g.chcolor(m.col.getRed(), m.col.getGreen(), m.col.getBlue(), 255);
		    g.rotimage(plp, p2c(ppc), plp.sz().div(2), -m.geta() - (Math.PI / 2));
		    g.chcolor();
		} catch(Loading l) {}
	    }
	}
    }

    public void drawparts(GOut g){
	drawmap(g);
	drawmarkers(g);
	boolean playerSegment = (sessloc != null) && ((curloc == null) || (sessloc.seg == curloc.seg));
	if(zoomlevel <= 2 && CFG.MMAP_GRID.get()) {drawgrid(g);}
	if(playerSegment && zoomlevel <= 1 && CFG.MMAP_VIEW.get()) {drawview(g);}
	if(playerSegment && CFG.MMAP_SHOW_PATH.get()) {drawMovement(g);}
	if(big && CFG.MMAP_POINTER.get()) {drawPointers(g);}
	if(dlvl <= 1)
	    drawicons(g);
	if(playerSegment) drawparty(g);
	if(CFG.MMAP_SHOW_BIOMES.get()) {drawbiome(g); }
    }

    public void draw(GOut g) {
	Location loc = this.curloc;
	if(loc == null)
	    return;
	redisplay(loc);
	remparty();
	drawparts(g);
    }

    private static boolean hascomplete(DisplayGrid[] disp, Area dext, Coord c) {
	DisplayGrid dg = disp[dext.ri(c)];
	if(dg == null)
	    return(false);
	return(dg.gref.get() != null);
    }

    protected boolean allowzoomout() {
	DisplayGrid[] disp = this.display;
	Area dext = this.dgext;
	try {
	    for(int x = dext.ul.x; x < dext.br.x; x++) {
		if(hascomplete(disp, dext, new Coord(x, dext.ul.y)) ||
		   hascomplete(disp, dext, new Coord(x, dext.br.y - 1)))
		    return(true);
	    }
	    for(int y = dext.ul.y; y < dext.br.y; y++) {
		if(hascomplete(disp, dext, new Coord(dext.ul.x, y)) ||
		   hascomplete(disp, dext, new Coord(dext.br.x - 1, y)))
		    return(true);
	    }
	} catch(Loading l) {
	    return(false);
	}
	return(false);
    }

    public DisplayIcon iconat(Coord c) {
	for(ListIterator<DisplayIcon> it = icons.listIterator(icons.size()); it.hasPrevious();) {
	    DisplayIcon disp = it.previous();
	    GobIcon.Image img = disp.img;
	    if((disp.sc != null) && c.isect(disp.sc.sub(img.cc), img.tex.sz()) && !filter(disp))
		return(disp);
	}
	return(null);
    }

    public DisplayMarker findmarker(long id) {
	for(DisplayGrid dgrid : display) {
	    if(dgrid == null)
		continue;
	    for(DisplayMarker mark : dgrid.markers(false, ui)) {
		if((mark.m instanceof SMarker) && (((SMarker)mark.m).oid == id))
		    return(mark);
	    }
	}
	return(null);
    }

    public DisplayMarker markerat(Coord tc) {
	for(DisplayGrid dgrid : display) {
	    if(dgrid == null)
		continue;
	    for(DisplayMarker mark : dgrid.markers(false, ui)) {
	        Area hit = mark.hit(ui);
		if((hit != null) && hit.contains(tc.sub(mark.m.tc).div(scalef())) && !filter(mark))
		    return(mark);
	    }
	}
	return(null);
    }

    public boolean filter(DisplayIcon icon) {
	MarkerID iattr = icon.gob.getattr(MarkerID.class);
	if((iattr != null) && (findmarker(iattr.id) != null))
	    return(true);
	return(false);
    }

    public boolean filter(DisplayMarker marker) {
	return(false);
    }

    public boolean clickloc(Location loc, int button, boolean press) {
	return(false);
    }

    public boolean clickicon(DisplayIcon icon, Location loc, int button, boolean press) {
	return(false);
    }

    public boolean clickmarker(DisplayMarker mark, Location loc, int button, boolean press) {
	return(false);
    }

    private UI.Grab drag;
    private boolean dragging;
    private Coord dsc, dmc;
    public boolean dragp(int button) {
	return(button == 1);
    }

    private Location dsloc;
    private DisplayIcon dsicon;
    private DisplayMarker dsmark;
    public boolean mousedown(Coord c, int button) {
	dsloc = xlate(c);
	if(dsloc != null) {
	    dsicon = iconat(c);
	    dsmark = markerat(dsloc.tc);
	    if((dsicon != null) && clickicon(dsicon, dsloc, button, true))
		return(true);
	    if((dsmark != null) && clickmarker(dsmark, dsloc, button, true))
		return(true);
	    if(clickloc(dsloc, button, true))
		return(true);
	} else {
	    dsloc = null;
	    dsicon = null;
	    dsmark = null;
	}
	if(dragp(button)) {
	    Location loc = curloc;
	    if((drag == null) && (loc != null)) {
		drag = ui.grabmouse(this);
		dsc = c;
		dmc = loc.tc;
		dragging = false;
	    }
	    return(true);
	}
	return(super.mousedown(c, button));
    }

    public void mousemove(Coord c) {
	if(drag != null) {
	    if(dragging) {
		setloc = null;
		follow = false;
		curloc = new Location(curloc.seg, dmc.add(dsc.sub(c).mul(scalef())));
	    } else if(c.dist(dsc) > 5) {
		dragging = true;
	    }
	}
	super.mousemove(c);
    }

    public boolean mouseup(Coord c, int button) {
	if((drag != null) && (button == 1)) {
	    drag.remove();
	    drag = null;
	}
	release: if(!dragging && (dsloc != null)) {
	    if((dsicon != null) && clickicon(dsicon, dsloc, button, false))
		break release;
	    if((dsmark != null) && clickmarker(dsmark, dsloc, button, false))
		break release;
	    if(clickloc(dsloc, button, false))
		break release;
	}
	dsloc = null;
	dsicon = null;
	dsmark = null;
	dragging = false;
	return(super.mouseup(c, button));
    }

    public boolean mousewheel(Coord c, int amount) {
	if(amount > 0) {
	    if(scale > 1) {
		scale--;
	    } else
	    if(allowzoomout())
		zoomlevel = Math.min(zoomlevel + 1, dlvl + 1);
	} else {
	    if(zoomlevel == 0 && scale < 4) {
		scale++;
	    }
	    zoomlevel = Math.max(zoomlevel - 1, 0);
	}
	return(true);
    }

    public Object tooltip(Coord c, Widget prev) {
	if(dloc != null) {
	    Coord tc = c.sub(sz.div(2)).mul(scalef()).add(dloc.tc);
	    DisplayMarker mark = markerat(tc);
	    if(mark != null) {
		return(mark.tip);
	    }
	    
	    DisplayIcon icon = iconat(c);
	    if(icon != null) {
		return icon.tooltip();
	    }
	    if(CFG.MMAP_POINTER.get()) {
		long curSeg = dloc.seg.id;
		for (IPointer p : pointers()) {
		    if(p.seg() == curSeg && p.sc(p2c(p.tc(curSeg)), sz).dist(c) < 20) {
			return p.tooltip();
		    }
		}
	    }
	}
	return(super.tooltip(c, prev));
    }

    public void mvclick(MapView mv, Coord mc, Location loc, Gob gob, int button) {
	if(mc == null) mc = ui.mc;
	if((sessloc != null) && (sessloc.seg == loc.seg)) {
	    if(gob == null)
		if(Config.always_true) {
		    Coord2d clickAt = loc.tc.sub(sessloc.tc).mul(tilesz).add(tilesz.div(2));
		    ui.pathQueue().ifPresent(pathQueue -> pathQueue.click(clickAt));
		    mv.click(clickAt, button, mc,
			clickAt.floor(posres),
			button, ui.modflags());
		} else
		mv.wdgmsg("click", mc,
			  loc.tc.sub(sessloc.tc).mul(tilesz).add(tilesz.div(2)).floor(posres),
			  button, ui.modflags());
	    else {
		if(button == 3) {FlowerMenu.lastGob(gob);}
		if(Config.always_true) {
		    Coord2d clickAt = loc.tc.sub(sessloc.tc).mul(tilesz).add(tilesz.div(2));
		    ui.pathQueue().ifPresent(pathQueue -> pathQueue.click(gob));
		    mv.click(clickAt, button, mc,
			clickAt.floor(posres), button, ui.modflags(), 0,
			(int) gob.id, gob.rc.floor(posres), 0, -1);
		} else
		mv.wdgmsg("click", mc,
			  loc.tc.sub(sessloc.tc).mul(tilesz).add(tilesz.div(2)).floor(posres),
			  button, ui.modflags(), 0,
			  (int)gob.id,
			  gob.rc.floor(posres),
			  0, -1);
	    }
	}
    }
    
    void drawgrid(GOut g) {
	int zmult = 1 << zoomlevel;
	Coord offset = sz.div(2).sub(dloc.tc.div(scalef()));
	Coord zmaps = cmaps.div( (float)zmult).mul(scale);
    
	double width = UI.scale(1f);
	Color col = g.getcolor();
	g.chcolor(Color.RED);
	for (int x = dgext.ul.x * zmult; x < dgext.br.x * zmult; x++) {
	    Coord a = UI.scale(zmaps.mul(x, dgext.ul.y * zmult)).add(offset);
	    Coord b = UI.scale(zmaps.mul(x, dgext.br.y * zmult)).add(offset);
	    if(a.x >= 0 && a.x <= sz.x) {
		a.y = Utils.clip(a.y, 0, sz.y);
		b.y = Utils.clip(b.y, 0, sz.y);
		g.line(a, b, width);
	    }
	}
	for (int y = dgext.ul.y * zmult; y < dgext.br.y * zmult; y++) {
	    Coord a = UI.scale(zmaps.mul(dgext.ul.x * zmult, y)).add(offset);
	    Coord b = UI.scale(zmaps.mul(dgext.br.x * zmult, y)).add(offset);
	    if(a.y >= 0 && a.y <= sz.y) {
		a.x = Utils.clip(a.x, 0, sz.x);
		b.x = Utils.clip(b.x, 0, sz.x);
		g.line(a, b, width);
	    }
	}
	g.chcolor(col);
    }
    
    public static final Coord VIEW_SZ = UI.scale(MCache.sgridsz.mul(9).div(tilesz.floor()));// view radius is 9x9 "server" grids
    public static final Color VIEW_BG_COLOR = new Color(255, 255, 255, 60);
    public static final Color VIEW_BORDER_COLOR = new Color(0, 0, 0, 128);
    
    void drawview(GOut g) {
	int zmult = 1 << zoomlevel;
	Coord2d sgridsz = new Coord2d(MCache.sgridsz);
	Gob player = ui.gui.map.player();
	if(player != null) {
	    Coord rc = p2c(player.rc.floor(sgridsz).sub(4, 4).mul(sgridsz));
	    Coord viewsz = VIEW_SZ.div(zmult).mul(scale);
	    g.chcolor(VIEW_BG_COLOR);
	    g.frect(rc, viewsz);
	    g.chcolor(VIEW_BORDER_COLOR);
	    g.rect(rc, viewsz);
	    g.chcolor();
	}
    }
    
    void drawMovement(GOut g) {
	if(ui.gui.pathQueue!=null) {
	    List<Pair<Coord2d, Coord2d>> lines = ui.gui.pathQueue.minimapLines();
	    g.chcolor(PathVisualizer.PathCategory.ME.color);
	    for (Pair<Coord2d, Coord2d> line : lines) {
		g.clippedLine(p2c(line.a), p2c(line.b), 1.5);
	    }
	    g.chcolor();
	}
    }
    
    void drawPointers(GOut g) {
	for (IPointer p : pointers()) {
	    if(curloc != null && p.seg() == curloc.seg.id) {
		p.drawmmarrow(g, p2c(p.tc(curloc.seg.id)), sz);
	    }
	}
	g.chcolor();
    }
    
    private List<IPointer> pointers() {
	if(curloc == null) {
	    return Collections.emptyList();
	}
	long curSeg = curloc.seg.id;
	return ui.gui.children().stream()
	    .filter(widget -> widget instanceof IPointer)
	    .map(widget -> (IPointer) widget)
	    .filter(p -> p.tc(curSeg) != null)
	    .collect(Collectors.toList());
    }
    
    void drawbiome(GOut g) {
	if(biometex != null) {
	    Coord mid = new Coord(g.sz().x / 2, 0);
	    Coord tsz = biometex.sz();
	    g.chcolor(BIOME_BG);
	    g.frect(mid.sub(2 + tsz.x /2, 0), tsz.add(4, 2));
	    g.chcolor();
	    g.aimage(biometex, mid, 0.5f, 0);
	}
    }
    
    private void setBiome(Location loc) {
	try {
	    String newbiome = biome;
	    if(loc == null) {
		Gob player = ui.gui.map.player();
		if(player != null) {
		    MCache mCache = ui.sess.glob.map;
		    int tile = mCache.gettile(player.rc.div(tilesz).floor());
		    Resource res = mCache.tilesetr(tile);
		    if(res != null) {
			newbiome = res.name;
		    }
		}
	    } else {
		MapFile map = loc.seg.file();
		if(map.lock.readLock().tryLock()) {
		    try {
			MapFile.Grid grid = loc.seg.grid(loc.tc.div(cmaps)).get();
			if(grid != null) {
			    int tile = grid.gettile(loc.tc.mod(cmaps));
			    newbiome = grid.tilesets[tile].res.name;
			}
		    } finally {
			map.lock.readLock().unlock();
		    }
		}
	    }
	    if(newbiome == null) {newbiome = "???";}
	    if(!newbiome.equals(biome)) {
		biome = newbiome;
		biometex = Text.renderstroked(Utils.prettyResName(biome)).tex();
	    }
	} catch (Loading ignored) {}
    }
    
    public interface IPointer {
	Coord2d tc(long id);
	
	Coord sc(Coord c, Coord sz);
	
	Object tooltip();
	
	long seg();
	
	void drawmmarrow(GOut g, Coord tc, Coord sz);
    }
}
