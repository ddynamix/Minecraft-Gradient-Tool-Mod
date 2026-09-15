package net.tyler.gradientwand.loot;

//? if <1.21 {
/*import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.loot.function.SetNbtLootFunction;
*///?} else {
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.loot.function.SetComponentsLootFunction;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.village.TradedItem;

import java.util.Optional;
//?}

import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LeafEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.enchantment.ModEnchantments;

import java.util.Set;

// These enchantments never turn up at an enchanting table, so without this there would be no way
// to find the books in survival at all. Two routes: chest loot, and a master librarian.
//
// 1.21 made enchantments data driven, so the mod holds RegistryKeys rather than Enchantment
// instances there and has to resolve an entry before it can build a book. The loot API's v3
// MODIFY event supplies exactly the registry lookup needed for that, which is why this version
// uses v3 while 1.20.1 stays on v2.
public class WandLoot {

    private static final Set<Identifier> CHESTS = Set.of(
            GradientWand.id("minecraft", "chests/stronghold_library"),
            GradientWand.id("minecraft", "chests/village/village_mason"),
            GradientWand.id("minecraft", "chests/woodland_mansion"),
            GradientWand.id("minecraft", "chests/desert_pyramid"));

    // Master is level 5, the last rung a librarian reaches, which suits a rare book
    private static final int MASTER = 5;

    // One pool roll per chest, and only a quarter of those produce anything
    private static final float CHEST_CHANCE = 0.25f;

    // Weighted towards the low levels: 60% level 1, 30% level 2, 10% level 3. Declared once so the
    // two versions cannot drift apart on the thing a player actually notices.
    private static final int WEIGHT_LEVEL_1 = 6;
    private static final int WEIGHT_LEVEL_2 = 3;
    private static final int WEIGHT_LEVEL_3 = 1;

    // 24, 33, then 42 emeralds, inside vanilla's 64 emerald ceiling at every level
    private static int priceFor(int level) {
        return 15 + level * 9;
    }

    public static void register() {
        registerChestLoot();
        registerTrades();
    }

    //? if <1.21 {
    /*private static void registerChestLoot() {
        // v2 on this version: five parameters, and the table is named by a plain Identifier
        LootTableEvents.MODIFY.register((resourceManager, lootManager, tableId, builder, source) -> {
            if (!source.isBuiltin() || !CHESTS.contains(tableId)) {
                return;
            }

            builder.pool(bookPool(ModEnchantments.CAPACITY));
            builder.pool(bookPool(ModEnchantments.STAMINA));
        });
    }

    private static void registerTrades() {
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.LIBRARIAN, MASTER, factories -> {
            factories.add(new BookTrade(ModEnchantments.CAPACITY));
            factories.add(new BookTrade(ModEnchantments.STAMINA));
        });
    }

    private static LootPool.Builder bookPool(Enchantment enchantment) {
        return LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1.0f))
                .conditionally(RandomChanceLootCondition.builder(CHEST_CHANCE))
                .with(levelled(enchantment, 1, WEIGHT_LEVEL_1))
                .with(levelled(enchantment, 2, WEIGHT_LEVEL_2))
                .with(levelled(enchantment, 3, WEIGHT_LEVEL_3));
    }

    // Each level is its own weighted entry, so exactly one is chosen. Stacking three SetNbt
    // functions on a single entry would leave whichever applied last and every book would be
    // level 3. The item is ENCHANTED_BOOK, not BOOK: an anvil only reads the former.
    private static LeafEntry.Builder<?> levelled(Enchantment enchantment, int level, int weight) {
        ItemStack book = EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(enchantment, level));

        return ItemEntry.builder(Items.ENCHANTED_BOOK)
                .weight(weight)
                .apply(SetNbtLootFunction.builder(book.getOrCreateNbt()));
    }

    // Emeralds plus a plain book for an enchanted one, the same shape as vanilla's own librarian
    // book trades. The level is rolled when the villager generates the offer, so cycling a
    // librarian for a particular level works exactly as players already expect.
    private record BookTrade(Enchantment enchantment) implements TradeOffers.Factory {

        @Override
        public TradeOffer create(Entity entity, Random random) {
            int level = 1 + random.nextInt(ModEnchantments.MAX_LEVEL);
            ItemStack book = EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(enchantment, level));

            return new TradeOffer(
                    new ItemStack(Items.EMERALD, priceFor(level)),
                    new ItemStack(Items.BOOK),
                    book,
                    12,
                    10,
                    0.2f);
        }
    }
    *///?} else {
    private static void registerChestLoot() {
        // v3 on this version: the table is named by a RegistryKey, and the fourth parameter is the
        // registry lookup. That lookup is the whole reason for using v3 here: an enchantment is a
        // dynamic registry entry now, and a loot pool is built long before any world exists.
        LootTableEvents.MODIFY.register((tableKey, builder, source, registries) -> {
            if (!source.isBuiltin() || !CHESTS.contains(tableKey.getValue())) {
                return;
            }

            RegistryWrapper.Impl<Enchantment> lookup = registries.getWrapperOrThrow(RegistryKeys.ENCHANTMENT);

            builder.pool(bookPool(lookup.getOrThrow(ModEnchantments.CAPACITY)));
            builder.pool(bookPool(lookup.getOrThrow(ModEnchantments.STAMINA)));
        });
    }

    private static void registerTrades() {
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.LIBRARIAN, MASTER, factories -> {
            factories.add(new BookTrade(ModEnchantments.CAPACITY));
            factories.add(new BookTrade(ModEnchantments.STAMINA));
        });
    }

    private static LootPool.Builder bookPool(RegistryEntry<Enchantment> enchantment) {
        return LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1.0f))
                .conditionally(RandomChanceLootCondition.builder(CHEST_CHANCE))
                .with(levelled(enchantment, 1, WEIGHT_LEVEL_1))
                .with(levelled(enchantment, 2, WEIGHT_LEVEL_2))
                .with(levelled(enchantment, 3, WEIGHT_LEVEL_3));
    }

    // forEnchantment already builds a correct enchanted book: ItemStack.addEnchantment routes
    // through EnchantmentHelper, which picks STORED_ENCHANTMENTS for a book. So the component is
    // read back off that book rather than assembled by hand.
    private static LeafEntry.Builder<?> levelled(RegistryEntry<Enchantment> enchantment, int level, int weight) {
        ItemStack book = EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(enchantment, level));

        ItemEnchantmentsComponent stored = book.getOrDefault(
                DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);

        return ItemEntry.builder(Items.ENCHANTED_BOOK)
                .weight(weight)
                .apply(SetComponentsLootFunction.builder(DataComponentTypes.STORED_ENCHANTMENTS, stored));
    }

    // The key is resolved per offer rather than up front: a villager always has a world, so the
    // registry is reachable here even though it is not when the trade list is registered.
    private record BookTrade(RegistryKey<Enchantment> key) implements TradeOffers.Factory {

        @Override
        public TradeOffer create(Entity entity, Random random) {
            int level = 1 + random.nextInt(ModEnchantments.MAX_LEVEL);

            RegistryEntry<Enchantment> enchantment = entity.getWorld()
                    .getRegistryManager()
                    .get(RegistryKeys.ENCHANTMENT)
                    .getEntry(key)
                    .orElseThrow();

            ItemStack book = EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(enchantment, level));

            return new TradeOffer(
                    new TradedItem(Items.EMERALD, priceFor(level)),
                    Optional.of(new TradedItem(Items.BOOK)),
                    book,
                    12,
                    10,
                    0.2f);
        }
    }
    //?}
}
