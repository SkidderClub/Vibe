package dev.vibe.hud;

import java.util.*;

/** Independent card lifetimes, reversible pop transitions and smoothly collapsing stack space. */
public final class TargetHudCards {
    private final LinkedHashMap<UUID,Card> cards=new LinkedHashMap<UUID,Card>();
    public static final class Card {
        public TargetHudData data;
        public float scale,offset;
        private float from;
        private boolean visible;
        private long changed;
        private Card(TargetHudData data) {this.data=data;}
        private float value(long now,int duration) {
            float t=Math.max(0,Math.min(1,(now-changed)/(float)duration));
            t=t*t*(3-2*t);
            return from+((visible?1:0)-from)*t;
        }
        private void show(boolean next,long now,int duration) {
            if(visible==next)return;
            from=value(now,duration);changed=now;visible=next;
        }
    }
    public List<Card> update(List<TargetHudData> live,TargetHudSettings settings,int height,int capacity,long now) {
        Set<UUID> active=new HashSet<UUID>();
        for(TargetHudData data:live)active.add(data.id);
        for(TargetHudData data:live) {
            Card card=cards.get(data.id);
            if(card==null) {
                if(cards.size()>=capacity) {
                    evict(active);
                }
                card=new Card(data);cards.put(data.id,card);
            }
            card.data=data;
        }
        while(cards.size()>capacity)evict(active);
        int duration=settings.animationDuration.getInt();
        float offset=0;
        Iterator<Card> iterator=cards.values().iterator();
        while(iterator.hasNext()) {
            Card card=iterator.next();
            boolean showing=active.contains(card.data.id);
            card.show(showing,now,duration);
            card.scale=settings.animations.isEnabled()?card.value(now,duration):showing?1:0;
            if(!showing&&card.scale<=.0001F) {iterator.remove();continue;}
            card.offset=offset;offset+=(height+settings.gap.getFloat())*card.scale;
        }
        return new ArrayList<Card>(cards.values());
    }
    public static float height(List<Card> cards,int rowHeight) {
        if(cards.isEmpty())return 0;
        Card last=cards.get(cards.size()-1);
        return last.offset+rowHeight*last.scale;
    }
    private void evict(Set<UUID> active) {
        UUID remove=null;
        for(UUID id:cards.keySet())if(!active.contains(id)) {remove=id;break;}
        if(remove==null)remove=cards.keySet().iterator().next();
        cards.remove(remove);
    }
    public void clear() {cards.clear();}
}
