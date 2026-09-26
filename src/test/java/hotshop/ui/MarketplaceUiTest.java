package hotshop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import hotshop.ApplicationRuntime;
import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.service.ListingDraft;
import hotshop.service.ListingPhoto;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Exercises real JavaFX controls against temporary SQLite data, without mocking services. */
class MarketplaceUiTest {
    @TempDir
    Path directory;
    private ApplicationRuntime runtime;
    private Stage stage;

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            started.countDown();
        });
        assertTrue(started.await(15, TimeUnit.SECONDS));
    }

    @BeforeEach
    void openWindow() throws Exception {
        runtime = ApplicationRuntime.open(directory);
        fx(() -> {
            stage = new Stage();
            new MarketplaceUi(stage, runtime);
            stage.show();
            return null;
        });
    }

    @AfterEach
    void closeWindow() throws Exception {
        fx(() -> {
            stage.hide();
            return null;
        });
        runtime.close();
    }

    @Test
    void register_validAccount_returnsToLoginAndOpensEmptySearchAfterLogin() throws Exception {
        snapshot("login-initial", 1100, 750);
        click("register-link");
        snapshot("register-minimum", 960, 640);
        type("username", "alice");
        type("display-name", "Alice");
        type("password", "Sample1!");
        type("confirm-password", "Sample1!");
        click("register-submit");
        awaitText("page-title", "Log in");
        assertEquals("alice", fx(() -> ((TextInputControl) stage.getScene().lookup("#username")).getText()));
        type("password", "Sample1!");
        click("login-submit");
        awaitText("page-title", "Search");
        assertEquals("Search for an item, or press Search to browse all listings.",
                fx(() -> ((Labeled) stage.getScene().lookup("#search-guidance")).getText()));
        assertTrue(fx(() -> stage.getScene().lookup("#nav-conversations").isDisabled()));
        assertEquals(960, fx(() -> stage.getMinWidth()));
        assertEquals(640, fx(() -> stage.getMinHeight()));
    }

    @Test
    void createListing_validForm_appearsInBuyerSearchAndPublicProfile() throws Exception {
        runtime.getAccounts().register("seller", "Sample1!", "Seller").join();
        runtime.getAccounts().register("buyer", "Sample1!", "Buyer").join();
        login("seller");
        click("nav-listings");
        awaitText("page-title", "My Listings");
        awaitReady();
        click("create-listing");
        awaitText("page-title", "Create Listing");
        awaitReady();
        type("title", "Wooden desk");
        type("description", "A sturdy desk");
        type("price", "45.50");
        type("pickup-location", "Campus gate");
        fx(() -> {
            ((ComboBox<?>) stage.getScene().lookup("#category")).getSelectionModel()
                    .select(Category.FURNITURE.ordinal());
            ((ComboBox<?>) stage.getScene().lookup("#condition")).getSelectionModel()
                    .select(Condition.GOOD.ordinal());
            return null;
        });
        snapshot("listing-editor-minimum", 960, 640);
        click("save-listing");
        awaitText("listing-title", "Wooden desk");
        awaitReady();
        snapshot("listing-owner-minimum", 960, 640);
        click("nav-logout");
        awaitText("page-title", "Log in");
        login("buyer");
        type("search-query", "desk");
        click("search-submit");
        awaitText("results-count", "1 listing");
        snapshot("search-results-minimum", 960, 640);
        click("listing-card");
        awaitText("listing-title", "Wooden desk");
        awaitReady();
        click("seller-profile");
        awaitText("profile-name", "Seller");
        awaitReady();
        snapshot("public-profile-minimum", 960, 640);
        assertTrue(fx(() -> stage.getScene().lookup("#listing-card") != null));
    }

    private void login(String username) throws Exception {
        type("username", username);
        type("password", "Sample1!");
        click("login-submit");
        awaitText("page-title", "Search");
        awaitReady();
    }

    @Test
    void offerAndComplete_twoParticipants_completesSaleThroughScreens() throws Exception {
        seedListing();
        login("buyer");
        click("search-submit");
        awaitText("results-count", "1 listing");
        click("listing-card");
        awaitText("listing-title", "Desk");
        awaitReady();
        click("make-offer");
        dialogType("offer-amount", "40.00");
        dialogClick("dialog-submit");
        awaitText("pending-offer", "Your pending offer: S$40.00");
        switchUser("seller");
        click("nav-listings");
        awaitReady();
        click("listing-card");
        awaitReady();
        confirm("accept-offer", "Accept Offer");
        awaitText("page-title", "Sale Details");
        awaitReady();
        snapshot("sale-details-minimum", 960, 640);
        confirm("sale-confirm-completion", "Confirm Completion");
        awaitText("sale-next-step", "Waiting for the other participant to confirm");
        switchUser("buyer");
        click("nav-purchases");
        awaitReady();
        click("sale-detail");
        awaitReady();
        confirm("sale-confirm-completion", "Confirm Completion");
        awaitText("sale-status", "Completed");
        assertEquals(hotshop.model.TransactionStatus.COMPLETED,
                runtime.getTransactions().getMyPurchases().join().getFirst().sale().getStatus());
    }

    private void seedListing() {
        runtime.getAccounts().register("seller", "Sample1!", "Seller").join();
        runtime.getAccounts().register("buyer", "Sample1!", "Buyer").join();
        runtime.getAccounts().login("seller", "Sample1!").join();
        runtime.getListings().createListing(new ListingDraft("Desk", "A desk", Category.FURNITURE,
                4500, Condition.GOOD, "Campus"), List.of()).join();
        runtime.getAccounts().logout().join();
    }

    @Test
    void listingCards_portraitAndLandscapePhotos_fitWholeImagesWithinCards() throws Exception {
        seedListing();
        runtime.getAccounts().login("seller", "Sample1!").join();
        for (int width : List.of(80, 320)) {
            BufferedImage photo = new BufferedImage(width, 160, BufferedImage.TYPE_INT_RGB);
            var graphics = photo.createGraphics();
            graphics.setColor(java.awt.Color.ORANGE);
            graphics.fillRect(0, 0, width, 160);
            graphics.setColor(java.awt.Color.BLUE);
            graphics.drawRect(0, 0, width - 1, 159);
            graphics.dispose();
            Path path = directory.resolve("photo-" + width + ".png");
            ImageIO.write(photo, "png", path.toFile());
            runtime.getListings().createListing(new ListingDraft("Photo " + width, "Description",
                    Category.FURNITURE, 4500, Condition.GOOD, "Campus"), List.of(ListingPhoto.add(path))).join();
        }
        runtime.getAccounts().logout().join();
        login("buyer");
        click("search-submit");
        awaitText("results-count", "3 listings");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (fx(() -> stage.getScene().getRoot().lookupAll(".image-view").size() < 2)
                && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        awaitReady();
        fx(() -> {
            int photos = 0;
            for (var node : stage.getScene().getRoot().lookupAll("#listing-card")) {
                Button card = (Button) node;
                var image = card.lookup(".image-view");
                if (image instanceof ImageView view) {
                    photos++;
                    var bounds = view.getBoundsInLocal();
                    double expectedRatio = card.getAccessibleText().startsWith("Photo 80") ? 0.5 : 2;
                    assertEquals(expectedRatio, bounds.getWidth() / bounds.getHeight(), 0.01);
                    assertTrue(bounds.getWidth() <= 208 && bounds.getHeight() <= 130);
                    assertEquals((208 - bounds.getWidth()) / 2, view.getBoundsInParent().getMinX(), 1);
                    assertEquals((130 - bounds.getHeight()) / 2, view.getBoundsInParent().getMinY(), 1);
                    assertNull(view.getViewport());
                    assertNull(view.getClip());
                }
            }
            assertEquals(2, photos);
            return null;
        });
        snapshot("cards-photos", 1400, 900);
    }

    @Test
    void listingCards_titlesWithinAndBeyondTwoLines_onlyTruncateOverflow() throws Exception {
        seedListing();
        String twoLines = "A comfortable wooden chair for studying";
        String overflowing = "Long title ".repeat(10).strip();
        runtime.getAccounts().login("seller", "Sample1!").join();
        for (String title : List.of(twoLines, overflowing, "W".repeat(120))) {
            runtime.getListings().createListing(new ListingDraft(title, "Description", Category.FURNITURE,
                    4500, Condition.GOOD, "Campus"), List.of()).join();
        }
        runtime.getAccounts().logout().join();
        login("buyer");
        click("search-submit");
        awaitText("results-count", "4 listings");
        awaitReady();
        fx(() -> {
            var titles = stage.getScene().getRoot().lookupAll(".listing-card-title");
            assertEquals(4, titles.size());
            for (var node : titles) {
                Label title = (Label) node;
                var rendered = (javafx.scene.text.Text) title.lookup(".text");
                String visible = rendered.getText().replace("\n", " ");
                if (title.getText().equals(overflowing) || title.getText().length() == 120) {
                    assertTrue(visible.endsWith("..."), visible);
                    assertTrue(rendered.getLayoutBounds().getHeight() > 30, "Overflow must use both lines");
                } else {
                    assertEquals(title.getText(), visible);
                }
                assertTrue(rendered.getLayoutBounds().getHeight() <= title.getHeight());
            }
            return null;
        });
        snapshot("cards-titles", 1400, 900);
    }

    @Test
    void listingCards_buyerAndOwner_showRelevantMetadata() throws Exception {
        seedListing();
        runtime.getAccounts().login("buyer", "Sample1!").join();
        var listing = runtime.getListings().searchListings(hotshop.service.ListingSearch.all()).join().getFirst();
        runtime.getOffers().submitOffer(listing.listing().getId(), 4000).join();
        runtime.getAccounts().logout().join();
        login("buyer");
        click("search-submit");
        awaitText("results-count", "1 listing");
        assertCardMetadata(List.of("Good"), List.of("Available", "1 pending offer", "1 pending offers"));
        click("listing-card");
        awaitReady();
        click("seller-profile");
        awaitText("profile-name", "Seller");
        awaitReady();
        assertCardMetadata(List.of("Good"), List.of("Available", "1 pending offer", "1 pending offers"));
        switchUser("seller");
        click("nav-listings");
        awaitReady();
        assertCardMetadata(List.of("Available", "1 pending offer"), List.of("Good"));
        snapshot("cards-owner-minimum", 960, 640);
        click("listing-card");
        awaitReady();
        confirm("accept-offer", "Accept Offer");
        awaitText("page-title", "Sale Details");
        awaitReady();
        click("nav-listings");
        awaitReady();
        assertCardMetadata(List.of("Reserved", "0 pending offers"), List.of("Good", "Available"));
    }

    private void assertCardMetadata(List<String> expected, List<String> absent) throws Exception {
        fx(() -> {
            Button card = (Button) stage.getScene().lookup("#listing-card");
            var labels = card.getGraphic().lookupAll(".label").stream()
                    .map(node -> ((Label) node).getText()).toList();
            expected.forEach(text -> assertTrue(labels.contains(text), "Missing card information: " + text));
            absent.forEach(text -> assertFalse(labels.contains(text), "Unexpected card information: " + text));
            return null;
        });
    }

    @Test
    void listingCards_mixedTitlesAndWindowSizes_keepUniformSizeAndReflow() throws Exception {
        seedListing();
        runtime.getAccounts().login("seller", "Sample1!").join();
        for (String title : List.of("A comfortable wooden chair for studying", "Long title ".repeat(10), "Lamp")) {
            runtime.getListings().createListing(new ListingDraft(title, "Description", Category.FURNITURE,
                    4500, Condition.GOOD, "Campus"), List.of()).join();
        }
        runtime.getAccounts().logout().join();
        login("buyer");
        click("search-submit");
        awaitText("results-count", "4 listings");
        snapshot("cards-minimum", 960, 640);
        double height = fx(() -> {
            var cards = stage.getScene().getRoot().lookupAll("#listing-card");
            double firstHeight = ((Button) cards.iterator().next()).getHeight();
            assertTrue(firstHeight < 350, "Cards should remain compact even with a maximum-length title");
            for (var node : cards) {
                Button card = (Button) node;
                assertEquals(240, card.getWidth(), 0.1);
                assertEquals(firstHeight, card.getHeight(), 0.1);
            }
            assertEquals(2, cards.stream().map(node -> node.getLayoutY()).distinct().count());
            return firstHeight;
        });
        snapshot("cards-wide", 1400, 900);
        fx(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            var cards = stage.getScene().getRoot().lookupAll("#listing-card");
            for (var node : cards) {
                Button card = (Button) node;
                assertEquals(240, card.getWidth(), 0.1);
                assertEquals(height, card.getHeight(), 0.1);
            }
            assertEquals(1, cards.stream().map(node -> node.getLayoutY()).distinct().count());
            return null;
        });
    }

    @Test
    void editListing_changedDetails_confirmsAndRejectsPendingOffers() throws Exception {
        seedListing();
        runtime.getAccounts().login("buyer", "Sample1!").join();
        var listing = runtime.getListings().searchListings(hotshop.service.ListingSearch.all()).join().getFirst();
        runtime.getOffers().submitOffer(listing.listing().getId(), 4000).join();
        runtime.getAccounts().logout().join();
        login("seller");
        click("nav-listings");
        awaitReady();
        click("listing-card");
        awaitReady();
        assertTrue(fx(() -> stage.getScene().lookup("#delete-listing").isDisabled()));
        click("edit-listing");
        awaitReady();
        type("title", "Updated desk");
        confirm("save-listing", "Save Listing");
        awaitText("listing-title", "Updated desk");
        awaitReady();
        assertEquals(hotshop.model.OfferStatus.REJECTED,
                runtime.getOffers().getOffersForListing(listing.listing().getId()).join()
                        .getFirst().offer().getStatus());
    }

    @Test
    void changePassword_wrongCurrentPassword_showsErrorBesideCurrentPassword() throws Exception {
        runtime.getAccounts().register("alice", "Sample1!", "Alice").join();
        login("alice");
        click("nav-profile");
        awaitReady();
        click("change-password");
        dialogType("current-password", "Wrong1!");
        dialogType("password", "Different1!");
        dialogType("confirm-password", "Different1!");
        dialogClick("dialog-submit");
        awaitText("page-status", "Invalid username or password");
        fx(() -> {
            var scene = Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                    .findFirst().orElseThrow().getScene();
            assertEquals("Invalid username or password",
                    ((Label) scene.lookup("#current-password-error")).getText());
            assertEquals("", ((Label) scene.lookup("#password-error")).getText());
            return null;
        });
        assertTrue(runtime.getAccounts().getCurrentUserId().join().isPresent());
    }

    @Test
    void changePassword_validConfirmation_retainsSessionAndAcceptsNewPassword() throws Exception {
        runtime.getAccounts().register("alice", "Sample1!", "Alice").join();
        login("alice");
        click("nav-profile");
        awaitReady();
        click("change-password");
        fx(() -> {
            var scene = Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                    .findFirst().orElseThrow().getScene();
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            var pane = (javafx.scene.control.DialogPane) scene.getRoot();
            assertEquals(javafx.scene.paint.Color.web("#faf7f2"), pane.getBackground().getFills().getFirst().getFill());
            assertNull(pane.getEffect());
            Button submit = (Button) scene.lookup("#dialog-submit");
            assertEquals(submit.getText(), ((javafx.scene.text.Text) submit.lookup(".text")).getText());
            saveSnapshot("password-dialog", scene);
            return null;
        });
        dialogType("current-password", "Sample1!");
        dialogType("password", "Different1!");
        dialogType("confirm-password", "Different1!");
        dialogClick("dialog-submit");
        awaitText("page-status", "Password changed. You remain logged in.");
        assertTrue(runtime.getAccounts().getCurrentUserId().join().isPresent());
        click("nav-logout");
        awaitText("page-title", "Log in");
        type("username", "alice");
        type("password", "Different1!");
        click("login-submit");
        awaitText("page-title", "Search");
    }

    @Test
    void back_unsubmittedInvalidFilter_preservesDraftWithoutChangingResults() throws Exception {
        seedListing();
        login("buyer");
        type("search-query", "desk");
        click("search-submit");
        awaitText("results-count", "1 listing");
        type("search-query", "unfinished query");
        type("minimum-price", "not a price");
        click("listing-card");
        awaitText("listing-title", "Desk");
        awaitReady();
        click("back");
        awaitText("results-count", "1 listing");
        assertEquals("unfinished query", fx(() ->
                ((TextInputControl) stage.getScene().lookup("#search-query")).getText()));
        assertEquals("not a price", fx(() ->
                ((TextInputControl) stage.getScene().lookup("#minimum-price")).getText()));
    }

    @Test
    void cancelSale_afterOneConfirmation_requiresOtherParticipantAndReleasesListing() throws Exception {
        seedListing();
        runtime.getAccounts().login("buyer", "Sample1!").join();
        var listing = runtime.getListings().searchListings(hotshop.service.ListingSearch.all()).join().getFirst();
        var offer = runtime.getOffers().submitOffer(listing.listing().getId(), 4000).join().offer();
        runtime.getAccounts().logout().join();
        runtime.getAccounts().login("seller", "Sample1!").join();
        var accepted = runtime.getOffers().acceptOffer(offer.getId()).join();
        runtime.getTransactions().confirmCompletion(accepted.transactionId()).join();
        runtime.getAccounts().logout().join();
        login("seller");
        click("nav-sales");
        awaitReady();
        click("sale-detail");
        awaitReady();
        click("sale-request-cancellation");
        awaitText("sale-next-step", "Waiting for the other participant to respond to your request");
        assertTrue(fx(() -> stage.getScene().lookup("#sale-confirm-completion").isDisabled()));
        switchUser("buyer");
        click("nav-purchases");
        awaitReady();
        click("sale-detail");
        awaitReady();
        confirm("sale-accept-cancellation", "Accept Cancellation");
        awaitText("sale-status", "Cancelled");
        assertEquals(hotshop.model.ListingStatus.AVAILABLE,
                runtime.getListings().getListing(listing.listing().getId()).join().listing().getStatus());
    }

    @Test
    void register_mismatchedPassword_keepsInputAndShowsFieldError() throws Exception {
        click("register-link");
        type("username", "alice");
        type("display-name", "Alice");
        type("password", "Sample1!");
        type("confirm-password", "Different1!");
        click("register-submit");
        awaitText("confirm-password-error", "Passwords do not match.");
        assertEquals("alice", fx(() -> ((TextInputControl) stage.getScene().lookup("#username")).getText()));
        assertTrue(runtime.getAccounts().getCurrentUserId().join().isEmpty());
    }

    @Test
    void navigate_dirtyProfile_cancelKeepsInputAndDiscardLeavesWithoutSaving() throws Exception {
        runtime.getAccounts().register("alice", "Sample1!", "Alice").join();
        login("alice");
        click("nav-profile");
        awaitReady();
        snapshot("own-profile-minimum", 960, 640);
        type("display-name", "Unsaved");
        confirm("nav-search", "Cancel");
        awaitText("page-title", "My Profile");
        assertEquals("Unsaved", fx(() -> ((TextInputControl) stage.getScene().lookup("#display-name")).getText()));
        confirm("nav-search", "Discard Changes");
        awaitText("page-title", "Search");
        assertEquals("Alice", runtime.getAccounts().getOwnProfile().join().getDisplayName());
    }

    private void switchUser(String username) throws Exception {
        awaitReady();
        click("nav-logout");
        awaitText("page-title", "Log in");
        login(username);
    }

    private void dialogType(String id, String value) throws Exception {
        fx(() -> {
            var scene = Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                    .findFirst().orElseThrow().getScene();
            scene.getRoot().applyCss();
            ((TextInputControl) scene.lookup("#" + id)).setText(value);
            return null;
        });
    }

    private void dialogClick(String id) throws Exception {
        fx(() -> {
            var scene = Window.getWindows().stream().filter(window -> window != stage && window.isShowing())
                    .findFirst().orElseThrow().getScene();
            scene.getRoot().applyCss();
            ((Button) scene.lookup("#" + id)).fire();
            return null;
        });
    }

    private void confirm(String id, String action) throws Exception {
        Platform.runLater(() -> ((Button) stage.getScene().lookup("#" + id)).fire());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            boolean handled = fx(() -> {
                for (Window window : List.copyOf(Window.getWindows())) {
                    if (window != stage && window.isShowing()) {
                        window.getScene().getRoot().applyCss();
                        for (var node : window.getScene().getRoot().lookupAll(".button")) {
                            if (node instanceof Button button && button.getText().equals(action)) {
                                if (action.equals("Accept Offer")) {
                                    window.getScene().getRoot().layout();
                                    assertEquals(action, ((javafx.scene.text.Text) button.lookup(".text")).getText());
                                    saveSnapshot("confirmation-dialog", window.getScene());
                                }
                                button.fire();
                                return true;
                            }
                        }
                    }
                }
                return false;
            });
            if (handled) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Confirmation not shown: " + action);
    }

    @Test
    void saveProfile_validText_persistsNameAndPrivatePickupPreference() throws Exception {
        runtime.getAccounts().register("alice", "Sample1!", "Alice").join();
        login("alice");
        click("nav-profile");
        awaitText("page-title", "My Profile");
        awaitReady();
        type("display-name", "Alice Tan");
        type("preferred-location", "Library lobby");
        click("save-profile");
        awaitText("page-status", "Profile saved.");
        assertEquals("Alice Tan", runtime.getAccounts().getOwnProfile().join().getDisplayName());
        assertEquals("Library lobby", runtime.getAccounts().getOwnProfile().join()
                .getPreferredPickupLocation().orElseThrow());
    }

    private void awaitReady() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (fx(() -> {
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                var body = stage.getScene().lookup("#page-body");
                return body != null && !body.isDisabled();
            })) {
                return;
            }
            Thread.sleep(25);
        }
        assertTrue(fx(() -> !stage.getScene().lookup("#page-body").isDisabled()));
    }

    private void snapshot(String name, int width, int height) throws Exception {
        fx(() -> {
            stage.setWidth(width);
            stage.setHeight(height);
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            return null;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (fx(() -> stage.getScene().getWidth() > width || stage.getScene().getWidth() < width - 40
                || stage.getScene().getHeight() > height || stage.getScene().getHeight() < height - 80)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Window did not resize to the requested dimensions");
            }
            Thread.sleep(25);
        }
        fx(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            saveSnapshot(name, stage.getScene());
            return null;
        });
    }

    private void saveSnapshot(String name, javafx.scene.Scene scene) throws Exception {
        var image = scene.snapshot(null);
        int imageWidth = (int) image.getWidth();
        int imageHeight = (int) image.getHeight();
        int[] pixels = new int[imageWidth * imageHeight];
        image.getPixelReader().getPixels(0, 0, imageWidth, imageHeight,
                PixelFormat.getIntArgbInstance(), pixels, 0, imageWidth);
        BufferedImage output = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, imageWidth, imageHeight, pixels, 0, imageWidth);
        Path folder = Path.of("build", "ui-checks");
        Files.createDirectories(folder);
        ImageIO.write(output, "png", folder.resolve(name + ".png").toFile());
    }

    private void type(String id, String value) throws Exception {
        fx(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            ((TextInputControl) stage.getScene().lookup("#" + id)).setText(value);
            return null;
        });
    }

    private void click(String id) throws Exception {
        fx(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            ((Button) stage.getScene().lookup("#" + id)).fire();
            return null;
        });
    }

    private void awaitText(String id, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (fx(() -> stage.getScene().lookup("#" + id) instanceof Labeled label
                    && label.getText().equals(expected))) {
                return;
            }
            Thread.sleep(25);
        }
        assertEquals(expected, fx(() -> ((Labeled) stage.getScene().lookup("#" + id)).getText()));
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
