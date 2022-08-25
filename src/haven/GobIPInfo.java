package haven;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class GobIPInfo extends GobInfo {
    private static final int PAD = UI.scale(3);
    
    private static final Color BG = new Color(0, 0, 0, 64);
    
    private int ipint;
    
//    private Gob gob;
    
    public GobIPInfo(Gob owner) {
	super(owner);
//	this.gob = owner;
	up(20);
	center = new Pair<>(0.5, 1.0);
    }
    
    @Override
    protected boolean enabled() {
	return CFG.SHOW_COMBAT_DMG.get();
    }
    
    @Override
    protected Tex render() {
//	if(ipint == 0 ) {return null;}
	
	BufferedImage ip = null;
//	if(ipint > 0) {
//	    ip = Text.std.render(String.format("%s", ipint), Color.cyan).img;
	    ip = Text.renderstroked(String.format("%s", ipint), Color.cyan, new Text.Foundry(Text.sans.deriveFont(Font.BOLD), 10)).img;
//	    gob.glob.sess.ui.message(String.format("rendering IP Tex with IP %s", ipint), GameUI.MsgType.GOOD);
//	}
	return new TexI(ItemInfo.catimgsh(PAD, PAD, BG, ip));
    }
    
    public void update(int c) {
	this.ipint = c;
//	render();
	clean();
//	gob.glob.sess.ui.message(String.format("updating in GobIPInfo ipint is %s", ipint), GameUI.MsgType.INFO);
//	Debug.log.println(String.format("Number %d, c: %d", v, c));
	//35071 - Initiative
    }
    
}
