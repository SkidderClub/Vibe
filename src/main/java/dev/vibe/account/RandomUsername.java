package dev.vibe.account;

import java.util.Locale;
import java.util.Random;
import java.security.SecureRandom;

/** Readable, bounded offline names. Camel-case word boundaries make every tuple unique. */
public final class RandomUsername {
    private static final String[] FIRST = "Amber Arctic Ashen Astral Azure Black Blaze Blithe Blue Bold Brave Breezy Bright Brisk Bronze Calm Cedar Cherry Chilly Chrome Cinder Clear Cloud Cobalt Coral Cosmic Cozy Cream Crisp Cyan Dapper Dark Dawn Dazzle Deep Denim Dewy Dream Dusky Dusty Ember Epic Fable Faint Fancy Fast Feral Fiery Flint Floral Fluffy Foggy Forest Frost Frozen Fuzzy Gentle Ghost Gilded Gleam Golden Grand Gray Green Happy Hasty Hazy Hidden Hollow Honey Humble Hyper Ice Icy Indigo Iron Ivory Jade Jolly Jovial Keen Kind Lunar Lucky Lush Magic Maple Marble Marine Mauve Meadow Merry Mild Mint Misty Moon Moss Mystic Navy Neon Night Nimble Noble Nova Ocean Olive Onyx Opal Orange Pale Peach Pearl Pine Pink Plum Polar Pretty Prime Proud Purple Quiet Rapid Raven Red Regal River Rocky Rogue Rose Royal Ruby Rust Rusty Sage Sandy Satin Shadow Shiny Silent Silky Silver Sky Slate Sleek Sleepy Smoky Snow Snowy Solar Sonic Spark Star Steel Storm Sunny Super Swift Teal Terra Tidal Tiny Toasty Topaz True Ultra Velvet Violet Vivid Warm White Wild Windy Wise Witty Wolf Yellow Young Zesty".split(" ");
    private static final String[] LAST = "Acorn Adder Alpaca Angel Antler Archer Arrow Aspen Badger Bamboo Bander Bass Bear Beaver Beetle Berry Birch Bison Bloom Boar Bolt Bonsai Breeze Brook Buck Bunny Cactus Canary Canyon Carp Cedar Cherry Chimp Cinder Cloud Cobra Comet Condor Coral Cosmos Cougar Coyote Crane Creek Cricket Crow Cub Dancer Dawn Deer Delta Dew Dove Dragon Drake Dream Druid Duck Dune Eagle Earth Echo Egret Elk Ember Falcon Fawn Fern Finch Fire Flame Flint Flower Fluke Forest Fox Frog Frost Galaxy Garden Gecko Gem Ghost Glade Goose Gopher Grove Gull Hawk Hazel Heron Holly Hornet Hound Husky Ibex Iris Ivy Jackal Jaguar Jay Jelly Jewel Kite Kiwi Koala Koi Lake Lark Laurel Leaf Lemur Lily Lion Lotus Lynx Mantis Maple Mare Marlin Marsh Meadow Meteor Mink Minnow Mist Moose Moth Mouse Nebula Newt Ninja Oak Oasis Ocean Olive Onyx Orca Orchid Orion Osprey Otter Owl Panda Panther Parrot Peach Pearl Pebble Petal Pine Piper Pixie Planet Plum Poppy Puma Quail Quartz Rabbit Raven Reef Robin Rocket Rose Rowan Runner Saber Sage Salmon Sand Scout Seal Seed Shark Shell Shrew Shrimp Sky Sloth Snake Snow Solar Spark Spear Spider Spirit Sprout Squid Star Stork Storm Swan Swift Thorn Tiger Toad Trout Tulip Turtle Valley Viper Wave Whale Willow Wolf Wombat Wren Yeti Zebra Zephyr".split(" ");
    private static final Random RANDOM = new SecureRandom();
    private RandomUsername() { }
    public static long possibilities() { return (long) FIRST.length * LAST.length * 1000; }
    public static String generate() { return generate(RANDOM); }
    public static String generate(Random random) {
        return FIRST[random.nextInt(FIRST.length)] + LAST[random.nextInt(LAST.length)]
                + String.format(Locale.ROOT, "%03d", random.nextInt(1000));
    }
}
