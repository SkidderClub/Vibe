package dev.vibe.phone;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class PhonePhotoLibraryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void installsAllSuppliedGalleryImagesOnceAndKeepsDeletions() throws Exception {
        Path folder = temporary.newFolder("starter").toPath();
        PhonePhotoLibrary library = new PhonePhotoLibrary(folder);
        try {
            library.installStarterPhotos(); library.awaitIdle();
            assertEquals("", library.error()); assertEquals(4, library.photos().size());
            for (PhonePhotoLibrary.Photo photo : library.photos()) {
                assertNotNull(javax.imageio.ImageIO.read(photo.file.toFile()));
                library.delete(photo);
            }
            library.awaitIdle(); assertEquals(0, library.photos().size());
        } finally { library.close(); }
        PhonePhotoLibrary reloaded = new PhonePhotoLibrary(folder);
        try {
            reloaded.installStarterPhotos(); reloaded.refresh(); reloaded.awaitIdle();
            assertEquals(0, reloaded.photos().size());
        } finally { reloaded.close(); }
    }
    @Test public void savesReloadsDecodesAndDeletesOnlyItsOwnPhotos() throws Exception {
        Path folder = temporary.newFolder("photos").toPath();
        PhonePhotoLibrary library = new PhonePhotoLibrary(folder);
        try {
            BufferedImage image = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
            image.setRGB(20, 10, 0xFF46A7CB);
            assertTrue(library.save(image)); library.awaitIdle();
            assertEquals("", library.error()); assertEquals(1, library.photos().size());
            PhonePhotoLibrary.Photo photo = library.photos().get(0);
            assertTrue(Files.isRegularFile(photo.file));
            assertNull(library.image(photo)); library.awaitIdle();
            assertEquals(0xFF46A7CB, library.image(photo).getRGB(20, 10));
            Path unrelated = folder.resolve("other.png"); Files.write(unrelated, new byte[]{1,2,3});
            Path external = temporary.newFile("outside.png").toPath();
            java.lang.reflect.Constructor<PhonePhotoLibrary.Photo> constructor = PhonePhotoLibrary.Photo.class.getDeclaredConstructor(Path.class, long.class, long.class);
            constructor.setAccessible(true);
            library.delete(constructor.newInstance(external, 0, 3)); library.awaitIdle();
            assertTrue(Files.exists(external));
            library.delete(photo); library.awaitIdle();
            assertEquals(0, library.photos().size()); assertTrue(Files.exists(unrelated));
            PhonePhotoLibrary reopened = new PhonePhotoLibrary(folder);
            try { reopened.refresh(); reopened.awaitIdle(); assertEquals(0, reopened.photos().size()); }
            finally { reopened.close(); }
        } finally { library.close(); }
    }
}
