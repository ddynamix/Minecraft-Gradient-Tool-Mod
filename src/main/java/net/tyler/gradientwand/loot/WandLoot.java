package net.tyler.gradientwand.loot;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LeafEntry;
import net.minecraft.loot.function.SetNbtLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.tyler.gradientwand.enchantment.ModEnchantments;

import java.util.Set;

// Treasure enchantments never turn up at an enchanting table, so without this there would be no
// way to find the books in survival at all. Two routes: chest loot, and a master librarian.
public class WandLoot {

    private static final Set<Identifier> CHESTS = Set.of(
            new Identifier("minecraft", "chests/stronghold_library"),
            new Identifier("minecraft", "chests/village/village_mason"),
            new Identifier("minecraft", "chests/woodland_mansion"),
            new Identifier("minecraft", "chests/desert_pyramid"));

    // Master is level 5, the last rung a librarian reaches, which suits a treasure enchantment
    private static final int MASTER = 5;

    public static void register() {
        registerChestLoot();
        registerTrades();
    }

    private static void registerChestLoot() {
        // Five parameters, not four: the resource manager and loot manager come first
        LootTableEvents.MODIFY.register((resourceManager, lootManager, tableId, builder, source) -> {
            if (!source.isBuiltin() || !CHESTS.contains(tableId)) {
                return;
            }

            builder.pool(bookPool(ModEnchantments.CAPACITY));
            builder.pool(bookPool(ModEnchantments.STAMINA));
        });
    }

    // Librarians are where players already go looking for treasure books, so both turn up there
    // rather than somewhere thematic but unlikely to be searched.
    private static void registerTrades() {
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.LIBRARIAN, MASTER, factories -> {
            factories.add(new BookTrade(ModEnchantments.CAPACITY));
            factories.add(new BookTrade(ModEnchantments.STAMINA));
        });
    }

    // A quarter chance of the book appearing, at one of the three levels. The levels are separate
    // weighted entries rather than three functions on one entry: SetNbt replaces the whole tag, so
    // stacking them would just leave whichever applied last and every book would come out level 3.
    private static LootPool.Builder bookPool(Enchantment enchantment) {
        return LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1.0f))
                .conditionally(RandomChanceLootCondition.builder(0.25f))
                .with(levelled(enchantment, 1, 6))
                .with(levelled(enchantment, 2, 3))
                .with(levelled(enchantment, 3, 1));
    }

    // SetNbt rather than EnchantRandomly, so the book always holds this exact enchantment.
    // Exactly one entry in the pool is chosen, weighted towards the lower levels.
    private static LeafEntry.Builder<?> levelled(Enchantment enchantment, int level, int weight) {
        ItemStack book = EnchantedBookItem.forEnchantment(new EnchantmentLevelEntry(enchantment, level));

        return ItemEntry.builder(Items.BOOK)
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

            // 24, 33, then 42 emeralds, inside vanilla's 64 emerald ceiling at every level
            int price = 15 + level * 9;

            return new TradeOffer(
                    new ItemStack(Items.EMERALD, price),
                    new ItemStack(Items.BOOK),
                    book,
                    12,
                    10,
                    0.2f);
        }
    }
}
