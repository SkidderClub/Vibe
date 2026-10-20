package dev.vibe.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.lwjgl.opengl.GL11;

/**
 * Small 1.8.9 renderer for the standard Minecraft JSON block-model format
 * served by Cosmetica.  It supports element rotation and rotated face UVs;
 * those are the two model features used by the public accessory catalog.
 */
public final class CosmeticaModel {
    private final List<Element> elements;
    private final float textureWidth, textureHeight;

    private CosmeticaModel(List<Element> elements, float textureWidth, float textureHeight) {
        this.elements = elements; this.textureWidth = textureWidth; this.textureHeight = textureHeight;
    }

    public static CosmeticaModel parse(String source) {
        JsonObject root = new JsonParser().parse(source).getAsJsonObject();
        float width = 64.0F, height = 64.0F;
        if (root.has("texture_size") && root.get("texture_size").isJsonArray()) {
            JsonArray size = root.getAsJsonArray("texture_size"); width = value(size, 0, width); height = value(size, 1, height);
        }
        List<Element> elements = new ArrayList<Element>();
        if (root.has("elements") && root.get("elements").isJsonArray()) for (JsonElement raw : root.getAsJsonArray("elements")) {
            if (!raw.isJsonObject()) continue;
            Element element = Element.parse(raw.getAsJsonObject()); if (element != null) elements.add(element);
        }
        return new CosmeticaModel(Collections.unmodifiableList(elements), Math.max(1.0F, width), Math.max(1.0F, height));
    }

    public void render(int frame, int frames) {
        int count = Math.max(1, frames);
        float frameHeight = 1.0F / count;
        float frameOffset = Math.floorMod(frame, count) * frameHeight;
        // Cosmetica's baked block-model renderer centres its 0..16 model
        // around the attachment by translating (-0.5,-0.5,-0.5). The vertex
        // conversion below already includes that centring, so there is no
        // extra universal vertical offset.
        GL11.glPushMatrix();
        try {
            for (Element element : elements) element.render(textureWidth, textureHeight, frameHeight, frameOffset);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static final class Element {
        private final float[] from, to, origin;
        private final String axis;
        private final float angle;
        private final List<Face> faces;
        private Element(float[] from, float[] to, float[] origin, String axis, float angle, List<Face> faces) { this.from=from; this.to=to; this.origin=origin; this.axis=axis; this.angle=angle; this.faces=faces; }
        private static Element parse(JsonObject raw) {
            float[] from = vector(raw, "from"), to = vector(raw, "to"); if (from == null || to == null) return null;
            float[] origin = new float[]{8,8,8}; String axis = ""; float angle = 0.0F;
            if (raw.has("rotation") && raw.get("rotation").isJsonObject()) { JsonObject rotation=raw.getAsJsonObject("rotation"); float[] parsed=vector(rotation,"origin"); if(parsed!=null)origin=parsed; axis=string(rotation,"axis"); angle=decimal(rotation,"angle"); }
            List<Face> faces = new ArrayList<Face>();
            if (raw.has("faces") && raw.get("faces").isJsonObject()) for (java.util.Map.Entry<String,JsonElement> entry : raw.getAsJsonObject("faces").entrySet()) {
                if (entry.getValue().isJsonObject()) { Face face=Face.parse(entry.getKey(),entry.getValue().getAsJsonObject()); if(face!=null)faces.add(face); }
            }
            return new Element(from,to,origin,axis,angle,faces);
        }
        private void render(float width, float height, float frameHeight, float frameOffset) {
            GL11.glPushMatrix();
            try {
                if (angle != 0.0F && !axis.isEmpty()) {
                    GL11.glTranslatef((origin[0]-8.0F)/16.0F, (origin[1]-8.0F)/16.0F, (origin[2]-8.0F)/16.0F);
                    // These coordinates are still in Cosmetica's authored
                    // model space at this point. The outer renderOnPart
                    // basis conversion happens later in the matrix chain, so
                    // every JSON rotation axis uses its authored angle.
                    GL11.glRotatef(angle, "x".equals(axis)?1:0, "y".equals(axis)?1:0, "z".equals(axis)?1:0);
                    GL11.glTranslatef(-(origin[0]-8.0F)/16.0F, -(origin[1]-8.0F)/16.0F, -(origin[2]-8.0F)/16.0F);
                }
                for (Face face : faces) face.render(from,to,width,height,frameHeight,frameOffset);
            } finally {
                // Every asset is third-party content. Never allow an invalid
                // element to leave a model-view matrix on the stack: that
                // corrupts later tile entities and eventually overflows GL.
                GL11.glPopMatrix();
            }
        }
    }

    private static final class Face {
        private final String direction; private final float[] uv; private final int rotation;
        private Face(String direction, float[] uv, int rotation) { this.direction=direction; this.uv=uv; this.rotation=rotation; }
        private static Face parse(String direction, JsonObject raw) {
            float[] uv = vector(raw, "uv");
            // A quad requires all four coordinates. Skipping malformed faces
            // is safer than throwing while an element matrix is active.
            return uv == null || uv.length < 4 ? null : new Face(direction, uv, (int) decimal(raw, "rotation"));
        }
        private void render(float[] from, float[] to, float width, float height, float frameHeight, float frameOffset) {
            float x1=(from[0]-8.0F)/16.0F, y1=(from[1]-8.0F)/16.0F, z1=(from[2]-8.0F)/16.0F;
            float x2=(to[0]-8.0F)/16.0F, y2=(to[1]-8.0F)/16.0F, z2=(to[2]-8.0F)/16.0F;
            float[][] points;
            // Keep the same vertex order as Cosmetica Core's FaceInfo. It is
            // deliberately not the generic quad order: it pairs with the
            // block-model UV order below and preserves authored textures.
            if ("north".equals(direction)) points=new float[][]{{x2,y2,z1},{x2,y1,z1},{x1,y1,z1},{x1,y2,z1}};
            else if ("south".equals(direction)) points=new float[][]{{x1,y2,z2},{x1,y1,z2},{x2,y1,z2},{x2,y2,z2}};
            else if ("east".equals(direction)) points=new float[][]{{x2,y2,z2},{x2,y1,z2},{x2,y1,z1},{x2,y2,z1}};
            else if ("west".equals(direction)) points=new float[][]{{x1,y2,z1},{x1,y1,z1},{x1,y1,z2},{x1,y2,z2}};
            else if ("up".equals(direction)) points=new float[][]{{x1,y2,z1},{x1,y2,z2},{x2,y2,z2},{x2,y2,z1}};
            else if ("down".equals(direction)) points=new float[][]{{x1,y1,z2},{x1,y1,z1},{x2,y1,z1},{x2,y1,z2}};
            else return;
            int turns=((rotation%360)+360)%360/90;
            GL11.glBegin(GL11.GL_QUADS);
            for(int index=0;index<4;index++) {
                int corner = (index + turns) & 3;
                float u = (corner < 2 ? uv[0] : uv[2]) / width;
                float v = frameOffset + ((corner == 1 || corner == 2 ? uv[3] : uv[1]) / height) * frameHeight;
                GL11.glTexCoord2f(u, v);
                GL11.glVertex3f(points[index][0],points[index][1],points[index][2]);
            }
            GL11.glEnd();
        }
    }

    private static float[] vector(JsonObject object,String key) { try { return vector(object.getAsJsonArray(key)); } catch(Exception ignored) { return null; } }
    private static float[] vector(JsonArray array) {
        if(array==null||array.size()<3)return null;
        float[] value = new float[array.size()];
        for (int index = 0; index < value.length; index++) value[index] = CosmeticaModel.value(array, index, 0.0F);
        return value;
    }
    private static float value(JsonArray array,int index,float fallback) { try{return array.size()>index?array.get(index).getAsFloat():fallback;}catch(Exception ignored){return fallback;} }
    private static String string(JsonObject object,String key){try{return object.has(key)?object.get(key).getAsString():"";}catch(Exception ignored){return "";}}
    private static float decimal(JsonObject object,String key){try{return object.has(key)?object.get(key).getAsFloat():0.0F;}catch(Exception ignored){return 0.0F;}}
}
