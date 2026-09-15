package net.tyler.gradientwand.loot;

//? if <1.21 {
/*import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
*///?} else {
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import net.minecraft.world.item.trading.ItemCost;

import java.util.Optional;
//?}

import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.tyler.gradientwand.GradientWand;
import net.tyler.gradientwand.enchantment.ModEnchantments;

import java.util.Set;

// These enchantments never turn up at an enchanting table, so without this there would be no way
// to find the books in survival at all. Two routes: chest loot, and a master librarian.
//
// 1.21 made enchantments data driven, so the mod holds Registries rather than Enchantment
// instances there and has to resolve an entry before it can build a book. The loot API's v3
// MODIFY event supplies exactly the registry lookup needed for that, which is why this version
// uses v3 while 1.20.1 stays on v2.
public class WandLoot {

    private static final Set<ResourceLocation> CHESTS = Set.of(
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
        // v2 on this version: five parameters, and the table is named by a plain ResourceLocation
        LootTableEvents.MODIFY.register((resourceManager, lootManager, tableId, builder, source) -> {
            if (!source.isBuiltin() || !CHESTS.contains(tableId)) {
                return;
            }

            builder.withPool(bookPool(ModEnchantments.CAPACITY));
            builder.withPool(bookPool(ModEnchantments.STAMINA));
        });
    }

    private static void registerTrades() {
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.LIBRARIAN, MASTER, factories -> {
            factories.add(new BookTrade(ModEnchantments.CAPACITY));
            factories.add(new BookTrade(ModEnchantments.STAMINA));
        });
    }

    private static LootPool.Builder bookPool(Enchantment enchantment) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0f))
                .when(LootItemRandomChanceCondition.randomChance(CHEST_CHANCE))
                .add(levelled(enchantment, 1, WEIGHT_LEVEL_1))
                .add(levelled(enchantment, 2, WEIGHT_LEVEL_2))
                .add(levelled(enchantment, 3, WEIGHT_LEVEL_3));
    }

    // Each level is its own weighted entry, so exactly one is chosen. Stacking three SetNbt
    // functions on a single entry would leave whichever applied last and every book would be
    // level 3. The item is ENCHANTED_BOOK, not BOOK: an anvil only reads the former.
    private static LootPoolSingletonContainer.Builder<?> levelled(Enchantment enchantment, int level, int weight) {
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));

        return LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                .setWeight(weight)
                .apply(SetNbtFunction.setTag(book.getOrCreateTag()));
    }

    // Emeralds plus a plain book for an enchanted one, the same shape as vanilla's own librarian
    // book trades. The level is rolled when the villager generates the offer, so cycling a
    // librarian for a particular level works exactly as players already expect.
    private record BookTrade(Enchantment enchantment) implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            int level = 1 + random.nextInt(ModEnchantments.MAX_LEVEL);
            ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));

            return new MerchantOffer(
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
        // v3 on this version: the table is named by a ResourceKey, and the fourth parameter is the
        // registry lookup. That lookup is the whole reason for using v3 here: an enchantment is a
        // dynamic registry entry now, and a loot pool is built long before any world exists.
        LootTableEvents.MODIFY.register((tableKey, builder, source, registries) -> {
            if (!source.isBuiltin() || !CHESTS.contains(tableKey.location())) {
                return;
            }

            HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);

            builder.withPool(bookPool(lookup.getOrThrow(ModEnchantments.CAPACITY)));
            builder.withPool(bookPool(lookup.getOrThrow(ModEnchantments.STAMINA)));
        });
    }

    private static void registerTrades() {
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.LIBRARIAN, MASTER, factories -> {
            factories.add(new BookTrade(ModEnchantments.CAPACITY));
            factories.add(new BookTrade(ModEnchantments.STAMINA));
        });
    }

    private static LootPool.Builder bookPool(Holder<Enchantment> enchantment) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0f))
                .when(LootItemRandomChanceCondition.randomChance(CHEST_CHANCE))
                .add(levelled(enchantment, 1, WEIGHT_LEVEL_1))
                .add(levelled(enchantment, 2, WEIGHT_LEVEL_2))
                .add(levelled(enchantment, 3, WEIGHT_LEVEL_3));
    }

    // forEnchantment already builds a correct enchanted book: ItemStack.addEnchantment routes
    // through EnchantmentHelper, which picks STORED_ENCHANTMENTS for a book. So the component is
    // read back off that book rather than assembled by hand.
    private static LootPoolSingletonContainer.Builder<?> levelled(Holder<Enchantment> enchantment, int level, int weight) {
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));

        ItemEnchantments stored = book.getOrDefault(
                DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);

        return LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                .setWeight(weight)
                .apply(SetComponentsFunction.setComponent(DataComponents.STORED_ENCHANTMENTS, stored));
    }

    // The key is resolved per offer rather than up front: a villager always has a world, so the
    // registry is reachable here even though it is not when the trade list is registered.
    private record BookTrade(ResourceKey<Enchantment> key) implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(Entity entity, RandomSource random) {
            int level = 1 + random.nextInt(ModEnchantments.MAX_LEVEL);

            Holder<Enchantment> enchantment = entity.level()
                    .registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(key);

            ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));

            return new MerchantOffer(
                    new ItemCost(Items.EMERALD, priceFor(level)),
                    Optional.of(new ItemCost(Items.BOOK)),
                    book,
                    12,
                    10,
                    0.2f);
        }
    }
    //?}
}
