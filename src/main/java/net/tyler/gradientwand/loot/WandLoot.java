package net.tyler.gradientwand.loot;

//? if <1.21 {
/*import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
*///?}
//? if >=1.21 && fabric {
/*import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.core.HolderLookup;
*///?}
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.world.item.trading.ItemCost;

import java.util.Optional;
//?}
//? if neoforge {
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
//?}

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
// The three builds reach chest loot completely differently. Fabric adds pools to the table as it
// loads (v2 on 1.20.1, v3 on 1.21 for the registry lookup a data-driven enchantment needs).
// NeoForge has no such event: it post-processes the stacks a table already rolled, through a
// global loot modifier declared in data/gradient_wand/loot_modifiers. Same books either way.
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
    // builds cannot drift apart on the thing a player actually notices.
    private static final int WEIGHT_LEVEL_1 = 6;
    private static final int WEIGHT_LEVEL_2 = 3;
    private static final int WEIGHT_LEVEL_3 = 1;

    // 24, 33, then 42 emeralds, inside vanilla's 64 emerald ceiling at every level
    private static int priceFor(int level) {
        return 15 + level * 9;
    }

    //? if fabric {
    /*public static void register() {
        registerChestLoot();
        registerTrades();
    }
    *///?}
    //? if neoforge {
    // Only the serializer needs registering: which tables get books is decided in doApply, and
    // the modifier instances themselves come from the JSON in data/gradient_wand/loot_modifiers.
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, GradientWand.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<WandBookModifier>> SERIALIZER =
            LOOT_MODIFIERS.register("wand_book", () -> WandBookModifier.CODEC);

    public static void register(IEventBus modBus) {
        LOOT_MODIFIERS.register(modBus);
    }

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LIBRARIAN) {
            return;
        }

        event.getTrades().get(MASTER).add(new BookTrade(ModEnchantments.CAPACITY));
        event.getTrades().get(MASTER).add(new BookTrade(ModEnchantments.STAMINA));
    }

    // Picks a level with the same 6/3/1 weighting the Fabric loot pool uses
    private static int weightedLevel(RandomSource random) {
        int roll = random.nextInt(WEIGHT_LEVEL_1 + WEIGHT_LEVEL_2 + WEIGHT_LEVEL_3);

        if (roll < WEIGHT_LEVEL_1) {
            return 1;
        }

        return roll < WEIGHT_LEVEL_1 + WEIGHT_LEVEL_2 ? 2 : 3;
    }

    // The JSON carries no conditions: the chest list lives in CHESTS above so that the Fabric and
    // NeoForge builds cannot disagree about which chests these books turn up in.
    public static class WandBookModifier extends LootModifier {

        public static final MapCodec<WandBookModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
                codecStart(instance)
                        .and(ResourceKey.codec(Registries.ENCHANTMENT)
                                .fieldOf("enchantment")
                                .forGetter(modifier -> modifier.enchantment))
                        .apply(instance, WandBookModifier::new));

        private final ResourceKey<Enchantment> enchantment;

        public WandBookModifier(LootItemCondition[] conditions, ResourceKey<Enchantment> enchantment) {
            super(conditions);

            this.enchantment = enchantment;
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            if (!CHESTS.contains(context.getQueriedLootTableId())) {
                return loot;
            }

            RandomSource random = context.getRandom();

            if (random.nextFloat() >= CHEST_CHANCE) {
                return loot;
            }

            Holder<Enchantment> holder = context.getLevel()
                    .registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(enchantment);

            loot.add(EnchantedBookItem.createForEnchantment(
                    new EnchantmentInstance(holder, weightedLevel(random))));

            return loot;
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return SERIALIZER.get();
        }
    }
    //?}

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
    *///?}
    //? if >=1.21 && fabric {
    /*private static void registerChestLoot() {
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

    // createForEnchantment already builds a correct enchanted book: the component is read back off
    // that book rather than assembled by hand.
    private static LootPoolSingletonContainer.Builder<?> levelled(Holder<Enchantment> enchantment, int level, int weight) {
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));

        ItemEnchantments stored = book.getOrDefault(
                DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);

        return LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                .setWeight(weight)
                .apply(SetComponentsFunction.setComponent(DataComponents.STORED_ENCHANTMENTS, stored));
    }
    *///?}
    //? if >=1.21 {
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
