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

import haven.Party.Member;

import java.util.*;
import java.util.Map.Entry;

public class Partyview extends Widget {
    long ign;
    Party party;
    Map<Long, Member> om = null;
    Member ol = null;
    Map<Member, Avaview> avs = new HashMap<Member, Avaview>();
    Button leave = null;
	
    @RName("pv")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new Partyview((Integer)args[0]));
	}
    }
	
    Partyview(long ign) {
	super(UI.scale(new Coord(84, 140)));
	this.ign = ign;
    }

    protected void added() {
	party = ui.sess.glob.party;
	update();
    }
    
    public boolean hasParty() {
        if (this.party != null) {
            return true;
	}
        else return false;
    }
	
    private void update() {
	if(party.memb != om) {
	    Collection<Member> old = new HashSet<Member>(avs.keySet());
	    for(final Member m : (om = party.memb).values()) {
	        Gob gob = m.getgob();
		if (CFG.PARTY_HIGHLIGHT.get() && om.size() > 1)
		    gob.highlightFoe(3);
		if(m.gobid == ign)
		    continue;
		Avaview w = avs.get(m);
		if(w == null) {
		    w = add(new Avaview(UI.scale(new Coord(27, 27)), m.gobid, "avacam") {
			    private Tex tooltip = null;
			    
			    public Object tooltip(Coord c, Widget prev) {
				Gob gob = m.getgob();
				if(gob == null)
				    return(tooltip);
				KinInfo ki = gob.getattr(KinInfo.class);
				if(ki == null)
				    return(null);
				return(tooltip = ki.rendered());
			    }
			});
		    w.canactivate = true;
		    avs.put(m, w);
		} else {
		    old.remove(m);
		}
	    }
	    for(Member m : old) {
		ui.destroy(avs.get(m));
		m.getgob().delattr(FoeHighlight.class);
		avs.remove(m);
	    }
	    List<Map.Entry<Member, Avaview>> wl = new ArrayList<Map.Entry<Member, Avaview>>(avs.entrySet());
	    Collections.sort(wl, new Comparator<Map.Entry<Member, Avaview>>() {
		    public int compare(Entry<Member, Avaview> a, Entry<Member, Avaview> b) {
			long aid = a.getKey().gobid, bid = b.getKey().gobid;
			if(aid < bid)
			    return(-1);
			else if(bid > aid)
			    return(1);
			return(0);
		    }
		});
	    int i = 0;
	    for(Map.Entry<Member, Avaview> e : wl) {
		e.getValue().c = UI.scale(new Coord((i % 2) * 43, (i / 2) * 43 + 27));
		i++;
	    }
	}
	for(Map.Entry<Member, Avaview> e : avs.entrySet()) {
	    e.getValue().color = e.getKey().col;
	}
	if((avs.size() > 0) && (leave == null)) {
	    leave = add(new Button(UI.scale(84), "Leave party"), Coord.z);
	}
	if((avs.size() == 0) && (leave != null)) {
	    ui.destroy(leave);
	    leave = null;
	}
    }
	
    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == leave) {
	    wdgmsg("leave");
	    return;
	}
	for(Member m : avs.keySet()) {
	    if(sender == avs.get(m)) {
		wdgmsg("click", (int)m.gobid, args[0]);
		return;
	    }
	}
	super.wdgmsg(sender, msg, args);
    }
	
    public void draw(GOut g) {
	update();
	super.draw(g);
    }
}
