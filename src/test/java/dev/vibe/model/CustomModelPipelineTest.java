package dev.vibe.model;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/** Loading, fitting and simplifying custom models without OpenGL. */
public class CustomModelPipelineTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void glbLoaderAppliesNodeTransformsAndReadsAttributes() throws Exception {
        File file = temporary.newFile("knife.glb");
        // A 90 degree turn around X lays the +Y knife along +Z.
        double half = Math.sqrt(0.5D);
        TestMeshes.knife().writeGlb(file, new double[] {half, 0, 0, half}, null);
        MeshData mesh = ModelLoader.load(file, ModelLoadOptions.defaults());
        assertEquals(1, mesh.primitives.size());
        assertEquals(5 * 12, mesh.triangleCount());
        float[] bounds = mesh.bounds();
        assertEquals(0.30F, bounds[5] - bounds[2], 1.0E-4F);
        assertEquals(0.024F, bounds[4] - bounds[1], 1.0E-4F);
        assertNotNull(mesh.primitives.get(0).uvs);
        assertTrue(mesh.definedUpAxis);
    }

    @Test
    public void glbSkinWeightsBecomeBodyPartsAndPivots() throws Exception {
        File file = temporary.newFile("rigged.glb");
        TestMeshes.humanoid(true, false).writeGlb(file, new double[] {0, 0, 0, 1}, TestMeshes.MIXAMO_JOINTS);
        MeshData mesh = ModelLoader.load(file, ModelLoadOptions.defaults());
        assertTrue("Mixamo joint names should be recognised", mesh.hasPartHints());
        byte[] parts = mesh.primitives.get(0).parts;
        float[] positions = mesh.primitives.get(0).positions;
        for (int vertex = 0; vertex < parts.length; vertex++) {
            if (positions[vertex * 3] < -0.5F) assertEquals(BodyPart.RIGHT_ARM.ordinal(), parts[vertex]);
            if (positions[vertex * 3 + 1] > 1.6F) assertEquals(BodyPart.HEAD.ordinal(), parts[vertex]);
        }
        assertTrue(mesh.jointPivots.containsKey(BodyPart.LEFT_ARM));
    }

    @Test
    public void objLoaderTriangulatesPolygonsAndReadsMaterials() throws Exception {
        File folder = temporary.newFolder("obj");
        Files.write(new File(folder, "box.mtl").toPath(), ("newmtl red\nKd 1 0 0\nd 1\nmap_Kd -s 1 1 1 missing.png\n")
                .getBytes(StandardCharsets.UTF_8));
        File obj = new File(folder, "box.obj");
        Files.write(obj.toPath(), ("mtllib box.mtl\nv 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\n"
                + "usemtl red\nf 1/1 2/2 3/3 4/4\nf -4/-4 -2/-2 -1/-1\n").getBytes(StandardCharsets.UTF_8));
        MeshData mesh = ModelLoader.load(obj, ModelLoadOptions.defaults());
        assertEquals(3, mesh.triangleCount());
        assertFalse(mesh.definedUpAxis);
        MeshData.Material material = mesh.materials.get(mesh.primitives.get(0).material);
        assertEquals(1.0F, material.color[0], 0.0F);
        assertEquals(0.0F, material.color[1], 0.0F);
        // OBJ's v = 0 is the bottom of the image; the loader flips it.
        assertEquals(1.0F, mesh.primitives.get(0).uvs[1], 1.0E-6F);
        assertNotNull("Normals are generated when the file has none", mesh.primitives.get(0).normals);
        assertEquals("missing.png", ObjLoader.texturePath("map_Kd -s 1 1 1 missing.png".split("\\s+")));
        assertEquals("my texture.png", ObjLoader.texturePath("map_Kd -clamp on my texture.png".split("\\s+")));
    }

    @Test
    public void swordFitterPutsTheBladeAlongTheSpriteDiagonal() {
        for (double[] rotation : new double[][] {{0, 0, 0, 1}, {0.5, 0.5, 0.5, 0.5}, {0, 0, 1, 0}, {0.3, -0.2, 0.6, 0.71}}) {
            TestMeshes.Builder knife = TestMeshes.knife();
            MeshData mesh = knife.build(false);
            mesh.transform(Matrix.fromTrs(new double[] {3, -2, 1}, rotation, new double[] {7, 7, 7}));
            double[] fit = SwordFitter.fit(mesh);
            // The original tip (top of the blade) and pommel (bottom of the handle) after fitting.
            double[] world = Matrix.multiply(fit, Matrix.multiply(Matrix.translation(3, -2, 1),
                    Matrix.multiply(Matrix.fromTrs(new double[] {0, 0, 0}, rotation, new double[] {1, 1, 1}), Matrix.scale(7, 7, 7))));
            double[] tip = Matrix.transformPoint(world, 0.002, 0.30, 0);
            double[] pommel = Matrix.transformPoint(world, 0, 0, 0);
            assertTrue("tip should be upper right: " + tip[0] + "," + tip[1], tip[0] > 0.7 && tip[1] > 0.7);
            assertTrue("pommel should be lower left: " + pommel[0] + "," + pommel[1], pommel[0] < 0.2 && pommel[1] < 0.2);
            assertEquals("the flat blade lies in the sprite plane", 0.5, tip[2], 0.02);
        }
    }

    @Test
    public void playerFitterSplitsATPoseAndLowersTheArms() {
        for (double yaw : new double[] {0, 90, 180, -90}) {
            TestMeshes.Builder builder = TestMeshes.humanoid(true, false);
            MeshData mesh = builder.build(false);
            mesh.transform(Matrix.rotation(yaw, 0, 1, 0));
            PreparedModel prepared = PlayerFitter.prepare(mesh, 60000, 0, true);
            assertEquals("a 108 triangle model needs no distant level", 1, prepared.lods.length);
            float[] rightArm = prepared.pivots[BodyPart.RIGHT_ARM.ordinal()];
            float[] leftArm = prepared.pivots[BodyPart.LEFT_ARM.ordinal()];
            // ModelBiped: right arm at negative x, shoulders near y = 0..4, feet at y = 24.
            assertTrue("yaw " + yaw + ": right arm on the -x side, was " + rightArm[0], rightArm[0] < -2.0F);
            assertTrue("yaw " + yaw + ": left arm on the +x side, was " + leftArm[0], leftArm[0] > 2.0F);
            assertTrue("shoulder height " + rightArm[1], rightArm[1] > -2.0F && rightArm[1] < 6.0F);
            float[] armBounds = bounds(prepared.lods[0].parts[BodyPart.RIGHT_ARM.ordinal()]);
            assertTrue("T-pose arm should hang down after the fix, lowest y " + armBounds[4], armBounds[4] > 10.0F);
            float[] legBounds = bounds(prepared.lods[0].parts[BodyPart.LEFT_LEG.ordinal()]);
            assertEquals("feet stand at y = 24", 24.0F, legBounds[4], 0.05F);
            float[] head = bounds(prepared.lods[0].parts[BodyPart.HEAD.ordinal()]);
            assertEquals("head top at y = -8", -8.0F, head[1], 0.05F);
            // Toes point forward, which is -z in ModelBiped space.
            assertTrue("toes face forward at yaw " + yaw, legBounds[2] < -2.0F && legBounds[5] < 3.0F);
        }
    }

    @Test
    public void playerFitterUsesSkinPartsWhenAvailable() {
        MeshData mesh = TestMeshes.humanoid(false, true).build(true);
        PreparedModel prepared = PlayerFitter.prepare(mesh, 60000, 0, false);
        for (BodyPart part : BodyPart.values()) {
            assertTrue(part + " should have geometry", prepared.lods[0].parts[part.ordinal()].length > 0);
        }
        assertTrue(prepared.notes.startsWith("rig"));
    }

    @Test
    public void densePlayerModelsGetADistantLevelOfDetail() {
        TestMeshes.Builder builder = TestMeshes.humanoid(false, false);
        builder.grid(60);
        PreparedModel prepared = PlayerFitter.prepare(builder.build(false), 60000, 0, false);
        assertEquals(2, prepared.lods.length);
        assertTrue(prepared.lods[1].triangles < prepared.lods[0].triangles / 2);
    }

    @Test
    public void simplifierRespectsTheTriangleBudget() {
        MeshData mesh = TestMeshes.builder().grid(120).build(false);
        assertEquals(120 * 120 * 2, mesh.triangleCount());
        PreparedModel.Lod lod = Simplifier.build(mesh, 3000, 1, null, null);
        assertTrue("got " + lod.triangles, lod.triangles <= 3000 && lod.triangles > 1000);
        PreparedModel.Lod full = Simplifier.build(mesh, 100000, 1, null, null);
        assertEquals(mesh.triangleCount(), full.triangles);
    }

    @Test
    public void jointNamesFromCommonRigsAreClassified() {
        assertEquals(BodyPart.LEFT_ARM, BodyPart.classifyJoint("mixamorig:LeftForeArm"));
        assertEquals(BodyPart.RIGHT_ARM, BodyPart.classifyJoint("upperarm_r"));
        assertEquals(BodyPart.LEFT_LEG, BodyPart.classifyJoint("Bip01 L Thigh"));
        assertEquals(BodyPart.RIGHT_LEG, BodyPart.classifyJoint("shin.R"));
        assertEquals(BodyPart.HEAD, BodyPart.classifyJoint("Head"));
        assertEquals(BodyPart.BODY, BodyPart.classifyJoint("clavicle_l"));
        assertEquals(BodyPart.BODY, BodyPart.classifyJoint("Spine1"));
        assertNull(BodyPart.classifyJoint("Armature"));
        assertNull(BodyPart.classifyJoint("ball_l"));
    }

    @Test
    public void sketchfabLinksResolveToIds() {
        assertEquals("2e4d663e1556469f95ca2203a069e081",
                SketchfabClient.parseUid("https://sketchfab.com/quift/collections/cs-knifes-2e4d663e1556469f95ca2203a069e081"));
        assertEquals("38185a07b3cb4818ac0419dbbe72527a",
                SketchfabClient.parseUid("https://sketchfab.com/3d-models/karambit-knife-freehand-38185a07b3cb4818ac0419dbbe72527a?x=1"));
        assertEquals("38185a07b3cb4818ac0419dbbe72527a", SketchfabClient.parseUid("38185A07B3CB4818AC0419DBBE72527A"));
        assertNull(SketchfabClient.parseUid("https://sketchfab.com/feed"));
        assertTrue(SketchfabClient.isCollectionUrl("https://sketchfab.com/a/collections/b-d60caf23f8604cef9bfe39bbe24c0b31"));
        assertEquals(ModelKind.SWORDS, CustomModelManager.guessKind("★ Karambit | Lore"));
        assertEquals(ModelKind.PLAYERS, CustomModelManager.guessKind("Darth Vader"));
    }

    @Test
    public void bundledCatalogHasBothDefaultCollectionsAndUniqueNames() throws Exception {
        ModelCatalog catalog = new ModelCatalog(new File(temporary.getRoot(), "none.json"));
        List<ModelCatalog.Entry> swords = catalog.entries(ModelKind.SWORDS), players = catalog.entries(ModelKind.PLAYERS);
        assertEquals(21, swords.size());
        assertTrue(players.size() > 100);
        assertNotNull("default sword", catalog.find(ModelKind.SWORDS, "Karambit Knife Freehand"));
        assertTrue(catalog.find(ModelKind.SWORDS, "Karambit Knife Freehand").downloadable);
        assertNotNull("default player", catalog.find(ModelKind.PLAYERS, "First order trooper Rigged and textured"));
        java.util.Set<String> names = new java.util.HashSet<String>();
        for (ModelCatalog.Entry entry : players) assertTrue("duplicate " + entry.name, names.add(entry.name.toLowerCase()));
        List<ModelCatalog.Entry> parsed = ModelCatalog.parse(new StringReader(
                "{\"collections\":[{\"kind\":\"swords\",\"models\":[{\"uid\":\"0123456789abcdef0123456789abcdef\",\"name\":\" A  b \"}]}]}"));
        assertEquals("A b", parsed.get(0).name);
    }

    @Test
    public void archivesAreExtractedSafely() throws Exception {
        File archive = temporary.newFile("model.zip");
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(archive));
        zip.putNextEntry(new java.util.zip.ZipEntry("../escape.txt"));
        zip.write(1);
        zip.putNextEntry(new java.util.zip.ZipEntry("source/model.obj"));
        zip.write("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".getBytes(StandardCharsets.UTF_8));
        zip.close();
        File target = temporary.newFolder("extracted");
        ModelFiles.extract(archive, target);
        assertFalse(new File(temporary.getRoot(), "escape.txt").exists());
        File model = ModelFiles.findModelFile(target);
        assertNotNull(model);
        assertEquals(1, ModelLoader.load(model, ModelLoadOptions.defaults()).triangleCount());
    }

    private static float[] bounds(PreparedModel.Batch[] batches) {
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (PreparedModel.Batch batch : batches) {
            for (int index = 0; index < batch.vertices.length; index += PreparedModel.STRIDE) {
                for (int axis = 0; axis < 3; axis++) {
                    bounds[axis] = Math.min(bounds[axis], batch.vertices[index + axis]);
                    bounds[axis + 3] = Math.max(bounds[axis + 3], batch.vertices[index + axis]);
                }
            }
        }
        return bounds;
    }
}
