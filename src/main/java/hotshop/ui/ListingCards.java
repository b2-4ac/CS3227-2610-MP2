package hotshop.ui;

import java.nio.file.Path;
import java.util.function.Supplier;

import hotshop.service.ListingWithSeller;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

/** Reusable cards whose TilePane wraps to the available viewport width. */
final class ListingCards {
    private static final double CARD_WIDTH = 240;
    private static final double CARD_HEIGHT = 304;
    private static final double CONTENT_WIDTH = 208;
    private static final double IMAGE_HEIGHT = 130;
    private static final double TITLE_HEIGHT = 52;

    private ListingCards() {
    }

    static TilePane grid() {
        TilePane grid = new TilePane(16, 16);
        grid.setPrefColumns(3);
        grid.setPrefTileWidth(CARD_WIDTH);
        grid.setPrefTileHeight(CARD_HEIGHT);
        grid.setTileAlignment(Pos.TOP_LEFT);
        return grid;
    }

    static Button card(MarketplaceUi app, ListingWithSeller value, Integer pending) {
        return card(app, value, pending, null);
    }

    /** A card with an optional extra line, such as a reserved listing's meetup summary. */
    static Button card(MarketplaceUi app, ListingWithSeller value, Integer pending, String meetupSummary) {
        var listing = value.listing();
        Supplier<Path> image = listing.getImages().isEmpty() ? null
                : () -> app.runtime.getListingImagePath(listing.getImages().getFirst().filename());
        Label title = UiControls.label(listing.getDetails().title(), "listing-card-title");
        title.setMinWidth(CONTENT_WIDTH);
        title.setPrefWidth(CONTENT_WIDTH);
        title.setMaxWidth(CONTENT_WIDTH);
        title.setMinHeight(TITLE_HEIGHT);
        title.setPrefHeight(TITLE_HEIGHT);
        title.setMaxHeight(TITLE_HEIGHT);
        title.setAlignment(Pos.TOP_LEFT);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        VBox details = new VBox(8, UiImages.display(image, CONTENT_WIDTH, IMAGE_HEIGHT), title,
                UiControls.label(UiControls.money(listing.getDetails().priceCents()), "price"));
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        if (pending != null) {
            footer.getChildren().addAll(UiControls.label(UiControls.title(listing.getStatus()), "badge"),
                    UiControls.label(pending + (pending == 1 ? " pending offer" : " pending offers"),
                            "listing-card-footer"));
        } else {
            footer.getChildren().add(UiControls.label(UiControls.title(listing.getDetails().condition()),
                    "listing-card-footer"));
        }
        details.getChildren().add(footer);
        if (meetupSummary != null) {
            var line = UiControls.label(meetupSummary, "muted");
            line.setId("listing-meetup-summary");
            details.getChildren().add(line);
        }
        Button card = UiControls.button("", "listing-card",
                () -> app.navigate(() -> app.listings.details(listing.getId())));
        card.setAccessibleText(listing.getDetails().title() + ", "
                + UiControls.money(listing.getDetails().priceCents()));
        card.setGraphic(details);
        details.setMinWidth(CONTENT_WIDTH);
        details.setPrefWidth(CONTENT_WIDTH);
        details.setMaxWidth(CONTENT_WIDTH);
        card.setMinSize(CARD_WIDTH, CARD_HEIGHT);
        card.setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        card.setMaxSize(CARD_WIDTH, CARD_HEIGHT);
        card.getStyleClass().addAll("card", "listing-card");
        return card;
    }
}
