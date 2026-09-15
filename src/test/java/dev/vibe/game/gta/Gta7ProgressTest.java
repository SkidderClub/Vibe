package dev.vibe.game.gta;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class Gta7ProgressTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void missingKeyPreservesBothEncryptedSavesAndCanBeRestored() throws Exception {
        Path file=temporary.newFolder().toPath().resolve("progress.json");
        Gta7Progress p=Gta7Progress.load(file);p.award(BigInteger.valueOf(850));assertTrue(p.save());
        byte[] key=Files.readAllBytes(Gta7SaveCodec.keyFile(file)),primary=Files.readAllBytes(file),backup=Files.readAllBytes(file.resolveSibling("progress.json.bak"));
        Files.delete(Gta7SaveCodec.keyFile(file));
        Gta7Progress unreadable=Gta7Progress.load(file);unreadable.award(BigInteger.TEN);assertFalse(unreadable.save());
        assertNotNull(unreadable.saveError());assertFalse(Files.exists(Gta7SaveCodec.keyFile(file)));
        assertArrayEquals(primary,Files.readAllBytes(file));assertArrayEquals(backup,Files.readAllBytes(file.resolveSibling("progress.json.bak")));
        p.award(BigInteger.ONE);assertFalse(p.save());assertArrayEquals(primary,Files.readAllBytes(file));
        Files.write(Gta7SaveCodec.keyFile(file),key);assertEquals(BigInteger.valueOf(850),Gta7Progress.load(file).xp());
        assertTrue(p.save());assertEquals(BigInteger.valueOf(851),Gta7Progress.load(file).xp());
    }
    @Test public void twoCorruptCopiesNeverGetOverwrittenByAnEmptyProfile() throws Exception {
        Path file=temporary.newFolder().toPath().resolve("progress.json");
        Gta7Progress p=Gta7Progress.load(file);p.award(BigInteger.valueOf(900));assertTrue(p.save());
        byte[] bad=Files.readAllBytes(file);bad[bad.length-1]^=1;
        Files.write(file,bad);Files.write(file.resolveSibling("progress.json.bak"),bad);
        Gta7Progress recovered=Gta7Progress.load(file);recovered.award(BigInteger.TEN);assertFalse(recovered.save());
        assertArrayEquals(bad,Files.readAllBytes(file));assertArrayEquals(bad,Files.readAllBytes(file.resolveSibling("progress.json.bak")));
    }
    @Test public void firstSaveHasARecoverableBackupAndBackupWriteFailureKeepsPrimary() throws Exception {
        Path file=temporary.newFolder().toPath().resolve("progress.json"),backup=file.resolveSibling("progress.json.bak");
        Gta7Progress p=Gta7Progress.load(file);p.award(BigInteger.valueOf(100));assertTrue(p.save());
        assertTrue(Gta7SaveCodec.encrypted(Files.readAllBytes(backup)));
        byte[] primary=Files.readAllBytes(file);Files.delete(backup);Files.createDirectory(backup);Files.write(backup.resolve("occupied"),new byte[]{1});
        p.award(BigInteger.TEN);assertFalse(p.save());assertArrayEquals(primary,Files.readAllBytes(file));
        Files.delete(backup.resolve("occupied"));Files.delete(backup);assertTrue(p.save());
        assertEquals(BigInteger.valueOf(110),Gta7Progress.load(file).xp());
    }

    @Test public void bankAndAllUpgradesSurviveRestartWithExplicitCaps() throws Exception {
        Path file=temporary.newFolder().toPath().resolve("progress.json");
        Gta7Progress progress=Gta7Progress.load(file);
        progress.award(BigInteger.TEN.pow(30));
        for(Gta7Progress.Upgrade upgrade:Gta7Progress.Upgrade.values()) {
            for(int i=0;i<15;i++) assertEquals(!upgrade.limited||i<10,progress.purchase(upgrade));
        }
        Gta7Progress loaded=Gta7Progress.load(file);
        assertEquals(progress.xp(),loaded.xp());
        for(Gta7Progress.Upgrade upgrade:Gta7Progress.Upgrade.values())assertEquals(progress.level(upgrade),loaded.level(upgrade));
        assertEquals(7,loaded.walkSpeed(),.00001);
        assertEquals("475",loaded.maxHealth().toPlainString());
        assertEquals("270",loaded.damage(0).toPlainString());
        assertEquals("148",loaded.damage(1).toPlainString());
    }

    @Test public void hugeLevelsAndPricesContinueBeyondMachineIntegerRange() throws Exception {
        Path file=temporary.newFolder().toPath().resolve("progress.json");
        String level="999999999999999999999999999999";
        String json="{\"version\":1,\"xp\":\""+BigInteger.TEN.pow(100)+"\",\"KNIFE\":\""+level+"\",\"AK47\":\""+level+"\",\"HEALTH\":\""+level+"\",\"SPEED\":\"0\"}";
        Files.write(file,json.getBytes(StandardCharsets.UTF_8));
        Gta7Progress p=Gta7Progress.load(file);
        for(Gta7Progress.Upgrade upgrade:new Gta7Progress.Upgrade[]{Gta7Progress.Upgrade.KNIFE,Gta7Progress.Upgrade.AK47,Gta7Progress.Upgrade.HEALTH}) {
            assertTrue(p.purchase(upgrade));assertEquals(new BigInteger(level).add(BigInteger.ONE),p.level(upgrade));
        }
        assertTrue(p.maxHealth().signum()>0);
        assertEquals(p.xp(),Gta7Progress.load(file).xp());
    }

    @Test public void corruptPrimaryRecoversPreviousBankWithoutOverwritingGoodBackup() throws Exception {
        Path file=temporary.newFolder().toPath().resolve("progress.json");
        Gta7Progress p=Gta7Progress.load(file);p.award(BigInteger.valueOf(100));assertTrue(p.save());
        p.award(BigInteger.valueOf(50));assertTrue(p.save());
        Files.write(file,"broken".getBytes(StandardCharsets.UTF_8));
        Gta7Progress recovered=Gta7Progress.load(file);
        assertEquals(BigInteger.valueOf(100),recovered.xp());
        assertTrue(Files.exists(file.resolveSibling("progress.json.corrupt")));
        recovered.award(BigInteger.TEN);assertTrue(recovered.save());
        assertEquals(BigInteger.valueOf(110),Gta7Progress.load(file).xp());
        assertEquals(BigInteger.valueOf(100),Gta7Progress.load(file.resolveSibling("progress.json.bak")).xp());
    }

    @Test public void failedWritesRemainRetryableAndUnaffordablePurchasesDoNotSpend() throws Exception {
        Path parent=temporary.newFile().toPath();
        Gta7Progress p=new Gta7Progress(parent.resolve("progress.json"));
        assertFalse(p.purchase(Gta7Progress.Upgrade.KNIFE));assertEquals(BigInteger.ZERO,p.xp());
        p.award(BigInteger.TEN);assertFalse(p.save());assertNotNull(p.saveError());
        Files.delete(parent);Files.createDirectory(parent);
        assertTrue(p.save());assertNull(p.saveError());
        assertEquals(BigInteger.TEN,Gta7Progress.load(parent.resolve("progress.json")).xp());
    }

    @Test public void encryptedSavesRejectEditsAndPlaintextDowngrades() throws Exception {
        Path file=temporary.newFolder().toPath().resolve("progress.json");
        Gta7Progress p=Gta7Progress.load(file);p.award(BigInteger.valueOf(123456));assertTrue(p.save());
        byte[] original=Files.readAllBytes(file);
        assertTrue(Gta7SaveCodec.encrypted(original));
        assertFalse(new String(original,StandardCharsets.ISO_8859_1).contains("123456"));
        p.award(BigInteger.ONE);assertTrue(p.save());
        byte[] changed=Files.readAllBytes(file);changed[changed.length-1]^=1;Files.write(file,changed);
        assertEquals(BigInteger.valueOf(123456),Gta7Progress.load(file).xp());
        Files.write(file,"{\"version\":1,\"xp\":\"999999999\",\"KNIFE\":\"0\",\"AK47\":\"0\",\"HEALTH\":\"0\",\"SPEED\":\"0\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(BigInteger.valueOf(123456),Gta7Progress.load(file).xp());
        assertTrue(Gta7SaveCodec.encrypted(Files.readAllBytes(file)));
        assertTrue(Gta7SaveCodec.encrypted(Files.readAllBytes(file.resolveSibling("progress.json.bak"))));
    }

    @Test public void legacyMigrationImmediatelyEncryptsBothPrimaryAndBackup() throws Exception {
        Path file=temporary.newFolder().toPath().resolve("progress.json");
        Files.write(file,"{\"version\":1,\"xp\":\"555\",\"KNIFE\":\"2\",\"AK47\":\"1\",\"HEALTH\":\"3\",\"SPEED\":\"4\"}".getBytes(StandardCharsets.UTF_8));
        Gta7Progress p=Gta7Progress.load(file);
        assertEquals(BigInteger.valueOf(555),p.xp());assertEquals(BigInteger.valueOf(2),p.level(Gta7Progress.Upgrade.KNIFE));
        assertEquals(BigInteger.ZERO,p.level(Gta7Progress.Upgrade.JET_PACK));
        assertTrue(Gta7SaveCodec.encrypted(Files.readAllBytes(file)));
        assertTrue(Gta7SaveCodec.encrypted(Files.readAllBytes(file.resolveSibling("progress.json.bak"))));
    }

    @Test public void skinsChargeOnceEquipIndependentlyAndSurviveRestart() throws Exception {
        Path file=temporary.newFolder().toPath().resolve("progress.json");
        Gta7Progress p=Gta7Progress.load(file);p.award(BigInteger.valueOf(1000));
        assertTrue(p.purchaseSkin(1,Gta7Progress.Skin.GOLD));assertEquals(BigInteger.valueOf(750),p.xp());
        assertTrue(p.purchaseSkin(1,Gta7Progress.Skin.STOCK));assertTrue(p.purchaseSkin(1,Gta7Progress.Skin.GOLD));
        assertEquals(BigInteger.valueOf(750),p.xp());assertTrue(p.purchaseSkin(0,Gta7Progress.Skin.ARCTIC));
        assertFalse(p.purchaseSkin(0,Gta7Progress.Skin.CRIMSON));
        Gta7Progress loaded=Gta7Progress.load(file);
        assertEquals(Gta7Progress.Skin.GOLD,loaded.skin(1));assertEquals(Gta7Progress.Skin.ARCTIC,loaded.skin(0));
        assertEquals(BigInteger.valueOf(400),loaded.xp());
    }

    @Test public void newUpgradeMathIsLinearAndMagazineDoesNotOverflow() throws Exception {
        Path file=temporary.newFolder().toPath().resolve("progress.json");Gta7Progress p=new Gta7Progress(file);
        p.award(BigInteger.TEN.pow(100));
        for(int i=0;i<10;i++){assertTrue(p.purchase(Gta7Progress.Upgrade.FAST_SHOOTING));assertTrue(p.purchase(Gta7Progress.Upgrade.PENETRATION));}
        assertEquals(.0525,p.fireInterval(),1e-10);assertEquals(.5,p.penetrationChance(),1e-10);
        assertFalse(p.purchase(Gta7Progress.Upgrade.FAST_SHOOTING));assertFalse(p.purchase(Gta7Progress.Upgrade.PENETRATION));
        com.google.gson.JsonObject root=new com.google.gson.JsonParser().parse(new String(Gta7SaveCodec.decrypt(file,Files.readAllBytes(file)),StandardCharsets.UTF_8)).getAsJsonObject();
        root.addProperty("MAX_AMMO",BigInteger.TEN.pow(30).toString());root.addProperty("JET_PACK",BigInteger.TEN.pow(30).toString());
        Files.write(file,Gta7SaveCodec.encrypt(file,root.toString().getBytes(StandardCharsets.UTF_8)));
        p=Gta7Progress.load(file);
        assertTrue(p.purchase(Gta7Progress.Upgrade.MAX_AMMO));assertTrue(p.purchase(Gta7Progress.Upgrade.JET_PACK));
        assertEquals(BigInteger.TEN.pow(30).add(BigInteger.ONE).multiply(BigInteger.valueOf(5)).add(BigInteger.valueOf(30)),p.magazineSize());
        assertEquals(BigInteger.TEN.pow(30).add(BigInteger.ONE),p.jetCapacity().toBigInteger());
    }
}
