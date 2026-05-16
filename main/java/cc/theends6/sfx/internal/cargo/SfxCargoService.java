package cc.theends6.sfx.internal.cargo;

import cc.theends6.sfx.api.item.SfxItems;
import cc.theends6.sfx.api.runtime.SfxRuntime;
import cc.theends6.sfx.internal.block.SfxAnchorRecord;
import cc.theends6.sfx.internal.block.SfxAnchoredInteraction;
import cc.theends6.sfx.internal.block.SfxBlockAnchorKey;
import cc.theends6.sfx.internal.block.SfxBlockDataService;
import cc.theends6.sfx.internal.block.SfxBlockInstanceRecord;
import cc.theends6.sfx.internal.block.SfxBlockLifecycleState;
import cc.theends6.sfx.internal.topology.SfxTopologyComponent;
import cc.theends6.sfx.internal.topology.SfxTopologyService;
import cc.theends6.sfx.internal.topology.SfxTopologyStatus;
import cc.theends6.sfx.internal.util.ItemBuilder;
import cc.theends6.sfx.internal.util.SfxBlockDrops;
import cc.theends6.sfx.internal.util.SfxEventGuards;
import cc.theends6.sfx.internal.util.SfxInteractionRules;
import cc.theends6.sfx.internal.util.SfxLocalization;
import cc.theends6.sfx.internal.util.Text;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainer;
import cc.theends6.sfx.internal.virtualcontainer.SfxVirtualContainerService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Keyed;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class SfxCargoService implements Listener {
    private static final int RANGE = 6;
    private static final int FILTER_INVENTORY_SIZE = 54;
    private static final int OUTPUT_INVENTORY_SIZE = 27;
    private static final int TRASH_INVENTORY_SIZE = 27;
    private static final long TICK_INTERVAL = 10L;
    private static final long FLUSH_INTERVAL = 20L;
    private static final int[] FILTER_SLOTS = {19, 20, 21, 28, 29, 30, 37, 38, 39};
    private static final Set<Integer> FILTER_SLOT_SET = Set.of(19, 20, 21, 28, 29, 30, 37, 38, 39);
    private static final int[] TRASH_INPUT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final Set<Integer> TRASH_INPUT_SLOT_SET = Set.of(10, 11, 12, 13, 14, 15, 16);

    private final JavaPlugin plugin;
    private final SfxRuntime runtime;
    private final SfxItems items;
    private final SfxLocalization localization;
    private final SfxBlockDataService blockData;
    private final SfxVirtualContainerService virtualContainers;
    private final Map<String, SfxCargoComponentDefinition> definitions = SfxCargoDefinitions.create();
    private final SfxTopologyService topology;
    private final Map<UUID, SfxCargoNodeState> states = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyStates = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Inventory> openMenus = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Integer>> distributionDebt = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public SfxCargoService(JavaPlugin plugin, SfxRuntime runtime, SfxItems items, SfxLocalization localization, SfxBlockDataService blockData, SfxVirtualContainerService virtualContainers) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.items = Objects.requireNonNull(items, "items");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.blockData = Objects.requireNonNull(blockData, "blockData");
        this.virtualContainers = Objects.requireNonNull(virtualContainers, "virtualContainers");
        this.topology = new SfxTopologyService(blockData, new SfxCargoTopologyPolicy(definitions), new SfxCargoConnectivityPolicy(RANGE));
        bootstrapLoadedStates();
        topology.rebuild();
        scheduleTick();
        scheduleFlush();
    }

    public boolean supportsType(String typeId) {
        return definitions.containsKey(typeId);
    }

    public boolean canPlace(String typeId, BlockPlaceEvent event) {
        SfxCargoComponentDefinition definition = definitions.get(typeId);
        if (definition == null) {
            return true;
        }
        if (!definition.isTerminal()) {
            return true;
        }
        BlockFace face = attachedFace(event);
        return face != null && isHorizontal(face);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        items.readMarker(event.getItemInHand()).ifPresent(marker -> {
            SfxCargoComponentDefinition definition = definitions.get(marker.itemId());
            if (definition == null) {
                return;
            }
            UUID instanceId = blockData.findAnchor(event.getBlockPlaced().getLocation())
                    .map(SfxAnchorRecord::instanceId)
                    .orElseGet(() -> blockData.registerSingleBlock(marker.itemId(), event.getBlockPlaced().getLocation(), event.getBlockPlaced().getType(), event.getPlayer().getUniqueId()));
            BlockFace face = attachedFace(event);
            SfxCargoNodeState state = SfxCargoNodeState.defaultFor(definition.type(), face == null ? BlockFace.NORTH : face);
            states.put(instanceId, state);
            dirtyStates.add(instanceId);
            persistState(instanceId, state);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction().isLeftClick() || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        SfxAnchoredInteraction interaction = SfxAnchoredInteraction.resolve(event, blockData);
        if (interaction == null) {
            return;
        }
        SfxBlockInstanceRecord instance = interaction.instance();
        SfxCargoComponentDefinition definition = definitions.get(instance.typeId());
        if (definition == null) {
            return;
        }
        boolean autoCrafterSelection = isAutoCrafter(definition.type())
                && event.getPlayer().isSneaking()
                && event.getItem() != null
                && !event.getItem().getType().isAir();
        if (!autoCrafterSelection && SfxInteractionRules.prefersBlockPlacement(items, event)) {
            return;
        }
        SfxEventGuards.denyBlockAndItemUse(event);
        runtime.executeForPlayer(event.getPlayer(), () -> handleInteract(event.getPlayer(), interaction.block(), instance, definition));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SfxTrashCanHolder) {
            if (event.getClickedInventory() != top || !TRASH_INPUT_SLOT_SET.contains(event.getRawSlot())) {
                event.setCancelled(true);
            }
            runtime.executeForPlayerLater(player, 1L, () -> clearTrash(top));
            return;
        }
        if (!(top.getHolder() instanceof SfxCargoSessionHolder holder)) {
            return;
        }
        SfxCargoComponentDefinition definition = typeDefinition(holder.type());
        if (definition == null) {
            return;
        }
        if (event.getClickedInventory() == top && FILTER_SLOT_SET.contains(event.getRawSlot()) && usesFilter(holder.type())) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top) {
            return;
        }
        SfxCargoNodeState state = currentState(holder.instanceId());
        int slot = event.getRawSlot();
        ClickType click = event.getClick();
        if (slot == 41 || slot == 42 || slot == 43 || slot == 13) {
            adjustChannel(state, click.isRightClick() ? -1 : 1, click.isShiftClick());
        } else if (slot == 15) {
            state.filterMode = state.filterMode.toggle();
        } else if (slot == 25) {
            state.matchLore = !state.matchLore;
        } else if (slot == 16) {
            state.smartFill = !state.smartFill;
        } else if (slot == 24) {
            state.distributionMode = state.distributionMode.toggle();
        } else if (slot == 34) {
            adjustPriority(state, click.isRightClick() ? -1 : 1, click.isShiftClick());
        } else if (slot == 31 && isAutoCrafter(holder.type())) {
            state.enabled = !state.enabled;
        }
        syncFilterFromInventory(top, state);
        persistState(holder.instanceId(), state);
        renderMenu(top, holder.instanceId(), holder.type(), state);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SfxTrashCanHolder) {
            for (int raw : event.getRawSlots()) {
                if (raw < top.getSize() && !TRASH_INPUT_SLOT_SET.contains(raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
            runtime.executeGlobalLater(1L, () -> clearTrash(top));
            return;
        }
        if (!(top.getHolder() instanceof SfxCargoSessionHolder holder)) {
            return;
        }
        if (!usesFilter(holder.type())) {
            event.setCancelled(true);
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize() && !FILTER_SLOT_SET.contains(raw)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof SfxCargoSessionHolder holder) {
            SfxCargoNodeState state = currentState(holder.instanceId());
            syncFilterFromInventory(top, state);
            persistState(holder.instanceId(), state);
            openMenus.remove(holder.instanceId());
        } else if (top.getHolder() instanceof SfxTrashCanHolder holder) {
            clearTrash(top);
            openMenus.remove(holder.instanceId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        
        
        openMenus.entrySet().removeIf(entry -> {
            Inventory inventory = entry.getValue();
            return inventory.getViewers().stream().noneMatch(viewer -> viewer.getUniqueId().equals(event.getPlayer().getUniqueId()));
        });
    }

    public void destroyAnchoredBlock(Block block, UUID instanceId, String typeId) {
        if (block == null || instanceId == null || typeId == null || !definitions.containsKey(typeId)) {
            return;
        }
        Inventory open = openMenus.remove(instanceId);
        if (open != null) {
            for (var viewer : List.copyOf(open.getViewers())) {
                viewer.closeInventory();
            }
        }
        SfxCargoNodeState state = states.get(instanceId);
        if (state == null) {
            SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
            state = instance == null ? new SfxCargoNodeState() : SfxCargoNodeState.decode(instance.stateBlob());
        }
        SfxCargoComponentDefinition definition = definitions.get(typeId);
        if (definition != null && usesFilter(definition.type())) {
            for (ItemStack stack : state.filterItems) {
                dropStack(block, stack);
            }
        }
        dropPluginBlock(block, typeId);
        states.remove(instanceId);
        dirtyStates.remove(instanceId);
        blockData.unregisterAt(block.getLocation());
    }

    public void shutdown() {
        running = false;
        for (Inventory inventory : List.copyOf(openMenus.values())) {
            for (var viewer : List.copyOf(inventory.getViewers())) {
                viewer.closeInventory();
            }
        }
        flushDirty();
        openMenus.clear();
        states.clear();
        distributionDebt.clear();
    }

    private void bootstrapLoadedStates() {
        for (SfxAnchorRecord anchor : blockData.anchors()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (instance == null || !definitions.containsKey(instance.typeId())) {
                continue;
            }
            states.put(instance.instanceId(), SfxCargoNodeState.decode(instance.stateBlob()));
        }
    }

    private void scheduleTick() {
        runtime.executeGlobalLater(TICK_INTERVAL, () -> {
            if (!running) {
                return;
            }
            tickCargo();
            tickAutoCrafters();
            clearOpenTrashMenus();
            scheduleTick();
        });
    }

    private void scheduleFlush() {
        runtime.executeGlobalLater(FLUSH_INTERVAL, () -> {
            if (!running) {
                return;
            }
            flushDirty();
            scheduleFlush();
        });
    }

    private void tickCargo() {
        virtualContainers.hydrateExternalBeforeLogic();
        topology.rebuildIfStale();
        for (SfxTopologyComponent component : topology.components()) {
            if (component.status() != SfxTopologyStatus.ONLINE || component.controllers().size() != 1 || component.terminals().isEmpty()) {
                continue;
            }
            List<NodeRef> inputs = new ArrayList<>();
            List<NodeRef> outputs = new ArrayList<>();
            for (UUID terminalId : component.terminals()) {
                SfxBlockInstanceRecord instance = blockData.findInstance(terminalId).orElse(null);
                if (instance == null) {
                    continue;
                }
                SfxCargoComponentDefinition definition = definitions.get(instance.typeId());
                if (definition == null) {
                    continue;
                }
                SfxCargoNodeState state = currentState(terminalId);
                if (definition.isInput()) {
                    inputs.add(new NodeRef(instance, definition, state));
                } else if (definition.isOutput()) {
                    outputs.add(new NodeRef(instance, definition, state));
                }
            }
            inputs.sort(Comparator.comparing(ref -> ref.instance.anchorKey(), this::compareAnchorKeys));
            outputs.sort(Comparator.comparing((NodeRef ref) -> ref.state.priority).reversed().thenComparing(ref -> ref.instance.anchorKey(), this::compareAnchorKeys));
            for (NodeRef input : inputs) {
                if (input.definition.type() == SfxCargoComponentType.ADVANCED_INPUT_NODE) {
                    processAdvancedInput(input, outputs);
                } else {
                    processBasicInput(input, outputs);
                }
            }
        }
        virtualContainers.pushDirtyAfterLogic();
    }

    private void processBasicInput(NodeRef input, List<NodeRef> outputs) {
        Endpoint source = resolveEndpoint(input.instance, input.state, false);
        if (source == null || source.container == null) {
            return;
        }
        Predicate<ItemStack> filter = stack -> acceptsInputFilter(input.state, stack);
        ItemStack stack = virtualContainers.withdrawFirst(source.container, filter, 64);
        if (isEmpty(stack)) {
            return;
        }
        ItemStack remainder = insertAcrossOutputs(input, stack, outputs, false);
        if (!isEmpty(remainder)) {
            virtualContainers.insert(source.container, remainder, false);
        }
    }

    private void processAdvancedInput(NodeRef input, List<NodeRef> outputs) {
        Endpoint source = resolveEndpoint(input.instance, input.state, false);
        if (source == null || source.container == null) {
            return;
        }
        Predicate<ItemStack> filter = stack -> acceptsInputFilter(input.state, stack);
        List<ItemStack> batch = virtualContainers.withdrawBatch(source.container, filter, input.state.maxItemsPerCycle, input.state.maxDistinctTypes);
        if (batch.isEmpty()) {
            return;
        }
        for (ItemStack stack : batch) {
            ItemStack remainder = insertAcrossOutputs(input, stack, outputs, true);
            if (!isEmpty(remainder)) {
                virtualContainers.insert(source.container, remainder, false);
            }
        }
    }

    private ItemStack insertAcrossOutputs(NodeRef input, ItemStack stack, List<NodeRef> outputs, boolean allowSplit) {
        if (isEmpty(stack)) {
            return null;
        }
        List<NodeRef> candidates = new ArrayList<>();
        for (NodeRef output : outputs) {
            if (output.state.channel != input.state.channel) {
                continue;
            }
            if (!acceptsOutputFilter(output.state, output.definition, stack)) {
                continue;
            }
            Endpoint endpoint = resolveEndpoint(output.instance, output.state, true);
            if (endpoint == null || endpoint.capacityFor(stack, output.state.smartFill) <= 0) {
                continue;
            }
            candidates.add(output.withEndpoint(endpoint));
        }
        if (candidates.isEmpty()) {
            return stack;
        }
        candidates.sort(Comparator.comparingInt((NodeRef ref) -> ref.priority()).reversed().thenComparing(ref -> ref.instance.anchorKey(), this::compareAnchorKeys));
        ItemStack remaining = stack.clone();
        int index = 0;
        while (index < candidates.size() && !isEmpty(remaining)) {
            int priority = candidates.get(index).priority();
            List<NodeRef> group = new ArrayList<>();
            while (index < candidates.size() && candidates.get(index).priority() == priority) {
                group.add(candidates.get(index++));
            }
            if (allowSplit && input.state.distributionMode == SfxCargoDistributionMode.EVEN) {
                remaining = insertEvenly(input, priority, group, remaining);
            } else {
                remaining = insertClassic(input, group, remaining);
            }
        }
        return isEmpty(remaining) ? null : remaining;
    }

    private ItemStack insertClassic(NodeRef input, List<NodeRef> group, ItemStack stack) {
        if (group.isEmpty() || isEmpty(stack)) {
            return stack;
        }
        int start = input.state.roundRobinCursor % group.size();
        ItemStack remaining = stack.clone();
        for (int i = 0; i < group.size(); i++) {
            NodeRef output = group.get((start + i) % group.size());
            remaining = output.endpoint.insert(remaining, output.state.smartFill);
            if (isEmpty(remaining)) {
                input.state.roundRobinCursor = (start + i + 1) % group.size();
                persistState(input.instance.instanceId(), input.state);
                return null;
            }
        }
        return remaining;
    }

    private ItemStack insertEvenly(NodeRef input, int priority, List<NodeRef> group, ItemStack stack) {
        if (group.isEmpty() || isEmpty(stack)) {
            return stack;
        }
        ItemStack remaining = stack.clone();
        String key = input.instance.instanceId() + ":" + input.state.channel + ":" + priority + ":" + SfxCargoItemKey.of(items, stack).key();
        Map<UUID, Integer> debts = distributionDebt.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        group.sort(Comparator.comparingInt((NodeRef ref) -> debts.getOrDefault(ref.instance.instanceId(), 0)).reversed()
                .thenComparing(ref -> ref.instance.anchorKey(), this::compareAnchorKeys));
        int original = remaining.getAmount();
        int totalMoved = 0;
        Map<UUID, Integer> movedByNode = new HashMap<>();
        while (!isEmpty(remaining)) {
            List<NodeRef> eligible = group.stream()
                    .filter(ref -> ref.endpoint.capacityFor(remaining, ref.state.smartFill) > 0)
                    .toList();
            if (eligible.isEmpty()) {
                break;
            }
            int base = Math.max(1, (int) Math.ceil(remaining.getAmount() / (double) eligible.size()));
            boolean any = false;
            for (NodeRef output : eligible) {
                if (isEmpty(remaining)) {
                    break;
                }
                int amount = Math.min(base, remaining.getAmount());
                int capacity = output.endpoint.capacityFor(remaining, output.state.smartFill);
                amount = Math.min(amount, capacity);
                if (amount <= 0) {
                    continue;
                }
                ItemStack part = remaining.clone();
                part.setAmount(amount);
                ItemStack partRemainder = output.endpoint.insert(part, output.state.smartFill);
                int moved = amount - (isEmpty(partRemainder) ? 0 : partRemainder.getAmount());
                if (moved <= 0) {
                    continue;
                }
                remaining.setAmount(remaining.getAmount() - moved);
                totalMoved += moved;
                movedByNode.merge(output.instance.instanceId(), moved, Integer::sum);
                any = true;
            }
            if (!any) {
                break;
            }
        }
        if (totalMoved > 0) {
            int eligibleCount = Math.max(1, group.size());
            int expected = totalMoved / eligibleCount;
            for (NodeRef node : group) {
                UUID id = node.instance.instanceId();
                int debt = debts.getOrDefault(id, 0);
                debt += expected - movedByNode.getOrDefault(id, 0);
                debts.put(id, Math.max(-4096, Math.min(4096, debt)));
            }
        }
        if (remaining.getAmount() <= 0 || totalMoved >= original) {
            return null;
        }
        return remaining;
    }

    private Endpoint resolveEndpoint(SfxBlockInstanceRecord node, SfxCargoNodeState state, boolean outputSide) {
        Location target = targetLocation(node.anchorKey(), state.attachedFace);
        if (target == null) {
            return null;
        }
        SfxAnchorRecord anchor = blockData.findAnchor(target).orElse(null);
        if (anchor != null) {
            SfxBlockInstanceRecord targetInstance = blockData.findInstance(anchor.instanceId()).orElse(null);
            if (targetInstance != null) {
                SfxCargoComponentDefinition targetDefinition = definitions.get(targetInstance.typeId());
                if (targetDefinition != null && targetDefinition.type() == SfxCargoComponentType.TRASH_CAN && outputSide) {
                    return trashEndpoint();
                }
            }
        }
        return virtualContainers.ensureRegistered(target).map(this::containerEndpoint).orElse(null);
    }

    private Location targetLocation(SfxBlockAnchorKey key, BlockFace face) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || face == null) {
            return null;
        }
        return new Location(world, key.x() + face.getModX(), key.y() + face.getModY(), key.z() + face.getModZ());
    }

    private boolean acceptsInputFilter(SfxCargoNodeState state, ItemStack stack) {
        return acceptsFilter(state, stack);
    }

    private boolean acceptsOutputFilter(SfxCargoNodeState state, SfxCargoComponentDefinition definition, ItemStack stack) {
        if (definition.type() != SfxCargoComponentType.ADVANCED_OUTPUT_NODE) {
            return true;
        }
        return acceptsFilter(state, stack);
    }

    private boolean acceptsFilter(SfxCargoNodeState state, ItemStack stack) {
        boolean anyFilter = false;
        boolean matched = false;
        for (ItemStack filter : state.filterItems) {
            if (isEmpty(filter)) {
                continue;
            }
            anyFilter = true;
            if (matches(filter, stack, state.matchLore)) {
                matched = true;
                break;
            }
        }
        if (state.filterMode == SfxCargoFilterMode.WHITELIST) {
            return anyFilter && matched;
        }
        return !matched;
    }

    private boolean matches(ItemStack filter, ItemStack stack, boolean matchLore) {
        if (isEmpty(filter) || isEmpty(stack)) {
            return false;
        }
        if (matchLore) {
            ItemStack a = filter.clone();
            ItemStack b = stack.clone();
            a.setAmount(1);
            b.setAmount(1);
            return a.isSimilar(b);
        }
        return filter.getType() == stack.getType() && items.readMarker(filter).map(marker -> marker.itemId()).equals(items.readMarker(stack).map(marker -> marker.itemId()));
    }

    private void tickAutoCrafters() {
        for (Map.Entry<UUID, SfxCargoNodeState> entry : states.entrySet()) {
            SfxBlockInstanceRecord instance = blockData.findInstance(entry.getKey()).orElse(null);
            if (instance == null) {
                continue;
            }
            SfxCargoComponentDefinition definition = definitions.get(instance.typeId());
            if (definition == null || !isAutoCrafter(definition.type())) {
                continue;
            }
            SfxCargoNodeState state = entry.getValue();
            if (!state.enabled || state.selectedRecipeKey == null || state.selectedRecipeKey.isBlank()) {
                continue;
            }
            if (definition.type() == SfxCargoComponentType.VANILLA_AUTO_CRAFTER) {
                tickVanillaAutoCrafter(instance, state);
            }
        }
    }

    private void tickVanillaAutoCrafter(SfxBlockInstanceRecord instance, SfxCargoNodeState state) {
        Recipe recipe = findRecipe(state.selectedRecipeKey);
        if (recipe == null) {
            return;
        }
        Location below = below(instance.anchorKey());
        if (below == null) {
            return;
        }
        SfxVirtualContainer container = virtualContainers.ensureRegistered(below).orElse(null);
        if (container == null) {
            return;
        }
        List<RecipeChoice> choices = recipeChoices(recipe);
        if (choices.isEmpty()) {
            return;
        }
        ItemStack result = recipe.getResult();
        if (isEmpty(result)) {
            return;
        }
        if (!canConsume(container, choices)) {
            return;
        }
        if (virtualContainers.capacityFor(container, result, false) < result.getAmount()) {
            return;
        }
        consumeChoices(container, choices);
        ItemStack remainder = virtualContainers.insert(container, result.clone(), false);
        if (!isEmpty(remainder)) {
            virtualContainers.insert(container, remainder, false);
        }
    }

    private List<RecipeChoice> recipeChoices(Recipe recipe) {
        List<RecipeChoice> choices = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> map = shaped.getChoiceMap();
            for (String row : shape) {
                for (int i = 0; i < row.length(); i++) {
                    RecipeChoice choice = map.get(row.charAt(i));
                    if (choice != null) {
                        choices.add(choice);
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            choices.addAll(shapeless.getChoiceList());
        }
        return choices;
    }

    private boolean canConsume(SfxVirtualContainer container, List<RecipeChoice> choices) {
        ItemStack[] mirror = cloneContents(container.rawMirror());
        for (RecipeChoice choice : choices) {
            if (!consumeOne(mirror, choice)) {
                return false;
            }
        }
        return true;
    }

    private void consumeChoices(SfxVirtualContainer container, List<RecipeChoice> choices) {
        ItemStack[] mirror = container.rawMirror();
        for (RecipeChoice choice : choices) {
            consumeOne(mirror, choice);
        }
        container.mirrorDirty(true);
    }

    private boolean consumeOne(ItemStack[] mirror, RecipeChoice choice) {
        for (int i = 0; i < mirror.length; i++) {
            ItemStack stack = mirror[i];
            if (isEmpty(stack) || !choice.test(stack)) {
                continue;
            }
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) {
                mirror[i] = null;
            }
            return true;
        }
        return false;
    }

    private Recipe findRecipe(String key) {
        java.util.Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof Keyed keyed)) {
                continue;
            }
            if (keyed.getKey().toString().equals(key)) {
                return recipe;
            }
        }
        return null;
    }

    private void handleInteract(Player player, Block block, SfxBlockInstanceRecord instance, SfxCargoComponentDefinition definition) {
        if (definition.type() == SfxCargoComponentType.MANAGER) {
            topology.rebuildIfStale();
            SfxTopologyComponent component = topology.componentForMember(instance.instanceId()).orElse(null);
            String status = component == null ? "NO_NETWORK" : component.status().name();
            player.sendMessage(Text.prefixed(plugin, "<yellow>Cargo Network:</yellow> <white>" + status + "</white>"));
            if (component != null) {
                player.sendMessage(Text.prefixed(plugin, "<gray>Backbone:</gray> <white>" + component.backboneNodes().size() + "</white> <gray>Terminals:</gray> <white>" + component.terminals().size() + "</white>"));
            }
            return;
        }
        if (definition.type() == SfxCargoComponentType.CONNECTOR) {
            topology.rebuildIfStale();
            boolean linked = topology.componentForMember(instance.instanceId()).isPresent();
            player.sendMessage(Text.prefixed(plugin, linked ? "<green>Cargo connector linked.</green>" : "<red>Cargo connector is detached.</red>"));
            return;
        }
        if (isAutoCrafter(definition.type()) && player.isSneaking() && player.getInventory().getItemInMainHand() != null && !player.getInventory().getItemInMainHand().getType().isAir()) {
            configureAutoCrafter(player, instance, definition, player.getInventory().getItemInMainHand());
            return;
        }
        if (definition.type() == SfxCargoComponentType.TRASH_CAN) {
            openTrashCan(player, instance);
            return;
        }
        openMenu(player, instance, definition.type());
    }

    private void configureAutoCrafter(Player player, SfxBlockInstanceRecord instance, SfxCargoComponentDefinition definition, ItemStack hand) {
        SfxCargoNodeState state = currentState(instance.instanceId());
        if (definition.type() == SfxCargoComponentType.VANILLA_AUTO_CRAFTER) {
            Recipe recipe = findRecipeByResult(hand);
            if (recipe instanceof Keyed keyed) {
                state.selectedRecipeKey = keyed.getKey().toString();
                persistState(instance.instanceId(), state);
                player.sendMessage(Text.prefixed(plugin, "<green>Selected vanilla recipe:</green> <white>" + state.selectedRecipeKey + "</white>"));
                return;
            }
            player.sendMessage(Text.prefixed(plugin, "<red>No keyed vanilla recipe was found for this item.</red>"));
            return;
        }
        String itemId = items.readMarker(hand).map(marker -> marker.itemId()).orElse("");
        if (itemId.isBlank()) {
            player.sendMessage(Text.prefixed(plugin, "<red>Hold a SFX item to select an enhanced/armor recipe target.</red>"));
            return;
        }
        state.selectedRecipeKey = itemId;
        persistState(instance.instanceId(), state);
        player.sendMessage(Text.prefixed(plugin, "<green>Selected recipe target:</green> <white>" + itemId + "</white>"));
    }

    private Recipe findRecipeByResult(ItemStack hand) {
        ItemStack probe = hand.clone();
        probe.setAmount(1);
        java.util.Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) {
                continue;
            }
            ItemStack result = recipe.getResult();
            if (isEmpty(result)) {
                continue;
            }
            ItemStack normalized = result.clone();
            normalized.setAmount(1);
            if (normalized.isSimilar(probe)) {
                return recipe;
            }
        }
        return null;
    }

    private void openMenu(Player player, SfxBlockInstanceRecord instance, SfxCargoComponentType type) {
        SfxCargoNodeState state = currentState(instance.instanceId());
        int size = type == SfxCargoComponentType.OUTPUT_NODE ? OUTPUT_INVENTORY_SIZE : FILTER_INVENTORY_SIZE;
        SfxCargoSessionHolder holder = new SfxCargoSessionHolder(instance.instanceId(), type);
        Inventory inventory = plugin.getServer().createInventory(holder, size, titleFor(type));
        holder.bind(inventory);
        renderMenu(inventory, instance.instanceId(), type, state);
        openMenus.put(instance.instanceId(), inventory);
        player.openInventory(inventory);
    }

    private void openTrashCan(Player player, SfxBlockInstanceRecord instance) {
        SfxTrashCanHolder holder = new SfxTrashCanHolder(instance.instanceId());
        Inventory inventory = plugin.getServer().createInventory(holder, TRASH_INVENTORY_SIZE, Text.mm("<dark_aqua>Trash Can</dark_aqua>"));
        holder.bind(inventory);
        renderTrash(inventory);
        openMenus.put(instance.instanceId(), inventory);
        player.openInventory(inventory);
    }

    private void renderMenu(Inventory inventory, UUID instanceId, SfxCargoComponentType type, SfxCargoNodeState state) {
        inventory.clear();
        if (type == SfxCargoComponentType.OUTPUT_NODE) {
            fillBorder(inventory, Material.GRAY_STAINED_GLASS_PANE);
            inventory.setItem(13, channelItem(state));
            return;
        }
        fillBorder(inventory, Material.GRAY_STAINED_GLASS_PANE);
        if (usesFilter(type)) {
            for (int i = 0; i < FILTER_SLOTS.length; i++) {
                inventory.setItem(FILTER_SLOTS[i], cloneOrNull(state.filterItems[i]));
            }
            inventory.setItem(15, modeItem(state));
            inventory.setItem(25, toggleItem(Material.WRITABLE_BOOK, "<yellow>Match lore/meta</yellow>", state.matchLore));
        }
        inventory.setItem(41, channelItem(state));
        inventory.setItem(42, channelItem(state));
        inventory.setItem(43, channelItem(state));
        if (type == SfxCargoComponentType.INPUT_NODE || type == SfxCargoComponentType.ADVANCED_INPUT_NODE) {
            inventory.setItem(16, toggleItem(Material.HOPPER, "<yellow>Smart fill</yellow>", state.smartFill));
            inventory.setItem(24, ItemBuilder.of(state.distributionMode == SfxCargoDistributionMode.EVEN ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name("<yellow>Distribution:</yellow> <white>" + state.distributionMode.name() + "</white>")
                    .lore("<gray>Classic: one destination per stack.</gray>", "<gray>Even: quota/debt fair split.</gray>", "<yellow>Click to toggle.</yellow>").build());
        }
        if (type == SfxCargoComponentType.ADVANCED_OUTPUT_NODE) {
            inventory.setItem(34, priorityItem(state.priority));
        }
        if (isAutoCrafter(type)) {
            inventory.setItem(31, toggleItem(Material.REDSTONE_TORCH, "<yellow>Enabled</yellow>", state.enabled));
            inventory.setItem(22, ItemBuilder.of(Material.CRAFTING_TABLE)
                    .name("<yellow>Selected recipe</yellow>")
                    .lore("<gray>" + (state.selectedRecipeKey == null || state.selectedRecipeKey.isBlank() ? "None" : state.selectedRecipeKey) + "</gray>", "<yellow>Sneak-right-click with target item to configure.</yellow>")
                    .build());
        }
    }

    private void renderTrash(Inventory inventory) {
        inventory.clear();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!TRASH_INPUT_SLOT_SET.contains(i)) {
                inventory.setItem(i, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
            }
        }
    }

    private void fillBorder(Inventory inventory, Material material) {
        ItemStack pane = ItemBuilder.of(material).name(" ").build();
        int size = inventory.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == size / 9 - 1 || col == 0 || col == 8) {
                inventory.setItem(i, pane);
            }
        }
    }

    private Component titleFor(SfxCargoComponentType type) {
        return switch (type) {
            case INPUT_NODE -> Text.mm("<red>Cargo Input</red>");
            case ADVANCED_INPUT_NODE -> Text.mm("<gold>Advanced Cargo Input</gold>");
            case OUTPUT_NODE -> Text.mm("<red>Cargo Output</red>");
            case ADVANCED_OUTPUT_NODE -> Text.mm("<gold>Advanced Cargo Output</gold>");
            case VANILLA_AUTO_CRAFTER -> Text.mm("<green>Auto-Crafter (Vanilla)</green>");
            case ENHANCED_AUTO_CRAFTER -> Text.mm("<green>Auto-Crafter (Enhanced)</green>");
            case ARMOR_AUTO_CRAFTER -> Text.mm("<green>Auto-Crafter (Armor Forge)</green>");
            default -> Text.mm("<yellow>Cargo</yellow>");
        };
    }

    private ItemStack channelItem(SfxCargoNodeState state) {
        return ItemBuilder.of(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                .name("<yellow>Channel:</yellow> <white>" + state.channel + "</white>")
                .lore("<yellow>Left-click:</yellow> <gray>+1</gray>", "<yellow>Right-click:</yellow> <gray>-1</gray>", "<yellow>Shift:</yellow> <gray>±4</gray>")
                .amount(Math.max(1, state.channel + 1))
                .build();
    }

    private ItemStack modeItem(SfxCargoNodeState state) {
        Material material = state.filterMode == SfxCargoFilterMode.WHITELIST ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        return ItemBuilder.of(material)
                .name("<yellow>Filter:</yellow> <white>" + state.filterMode.name() + "</white>")
                .lore("<gray>Whitelist accepts only listed items.</gray>", "<gray>Blacklist blocks listed items.</gray>", "<yellow>Click to toggle.</yellow>")
                .build();
    }

    private ItemStack toggleItem(Material material, String name, boolean enabled) {
        return ItemBuilder.of(enabled ? Material.LIME_STAINED_GLASS_PANE : material)
                .name(name + " <white>" + (enabled ? "ON" : "OFF") + "</white>")
                .lore("<yellow>Click to toggle.</yellow>")
                .build();
    }

    private ItemStack priorityItem(int priority) {
        return ItemBuilder.of(priorityMaterial(priority))
                .name("<yellow>Priority:</yellow> <white>" + priority + " / 16</white>")
                .lore("<gray>Higher priority outputs receive items first.</gray>", "<yellow>Left-click:</yellow> <gray>+1</gray>", "<yellow>Right-click:</yellow> <gray>-1</gray>", "<yellow>Shift:</yellow> <gray>±4</gray>")
                .amount(priority)
                .build();
    }

    private Material priorityMaterial(int priority) {
        return switch (SfxCargoNodeState.clamp(priority, 1, 16)) {
            case 1 -> Material.WHITE_STAINED_GLASS;
            case 2 -> Material.LIGHT_GRAY_STAINED_GLASS;
            case 3 -> Material.GRAY_STAINED_GLASS;
            case 4 -> Material.BLACK_STAINED_GLASS;
            case 5 -> Material.BROWN_STAINED_GLASS;
            case 6 -> Material.RED_STAINED_GLASS;
            case 7 -> Material.ORANGE_STAINED_GLASS;
            case 8 -> Material.YELLOW_STAINED_GLASS;
            case 9 -> Material.LIME_STAINED_GLASS;
            case 10 -> Material.GREEN_STAINED_GLASS;
            case 11 -> Material.CYAN_STAINED_GLASS;
            case 12 -> Material.LIGHT_BLUE_STAINED_GLASS;
            case 13 -> Material.BLUE_STAINED_GLASS;
            case 14 -> Material.PURPLE_STAINED_GLASS;
            case 15 -> Material.MAGENTA_STAINED_GLASS;
            default -> Material.PINK_STAINED_GLASS;
        };
    }

    private void syncFilterFromInventory(Inventory inventory, SfxCargoNodeState state) {
        if (inventory == null || state == null || inventory.getSize() < FILTER_INVENTORY_SIZE) {
            return;
        }
        for (int i = 0; i < FILTER_SLOTS.length; i++) {
            ItemStack stack = inventory.getItem(FILTER_SLOTS[i]);
            if (isEmpty(stack)) {
                state.filterItems[i] = null;
            } else {
                ItemStack clone = stack.clone();
                clone.setAmount(1);
                state.filterItems[i] = clone;
            }
        }
    }

    private void adjustChannel(SfxCargoNodeState state, int delta, boolean shift) {
        int step = shift ? 4 : 1;
        state.channel = Math.floorMod(state.channel + (delta * step), 16);
    }

    private void adjustPriority(SfxCargoNodeState state, int delta, boolean shift) {
        int step = shift ? 4 : 1;
        state.priority = SfxCargoNodeState.clamp(state.priority + delta * step, 1, 16);
    }

    private void clearOpenTrashMenus() {
        for (Inventory inventory : openMenus.values()) {
            if (inventory.getHolder() instanceof SfxTrashCanHolder) {
                clearTrash(inventory);
            }
        }
    }

    private void clearTrash(Inventory inventory) {
        if (inventory == null) {
            return;
        }
        for (int slot : TRASH_INPUT_SLOTS) {
            inventory.setItem(slot, null);
        }
    }

    private SfxCargoNodeState currentState(UUID instanceId) {
        SfxCargoNodeState cached = states.get(instanceId);
        if (cached != null) {
            return cached;
        }
        SfxBlockInstanceRecord instance = blockData.findInstance(instanceId).orElse(null);
        SfxCargoNodeState decoded = instance == null ? new SfxCargoNodeState() : SfxCargoNodeState.decode(instance.stateBlob());
        states.put(instanceId, decoded);
        return decoded;
    }

    private void persistState(UUID instanceId, SfxCargoNodeState state) {
        if (instanceId == null || state == null) {
            return;
        }
        states.put(instanceId, state);
        dirtyStates.add(instanceId);
    }

    private void flushDirty() {
        for (UUID instanceId : List.copyOf(dirtyStates)) {
            SfxCargoNodeState state = states.get(instanceId);
            if (state == null) {
                dirtyStates.remove(instanceId);
                continue;
            }
            blockData.updateInstanceState(instanceId, state.encode(), SfxBlockLifecycleState.IDLE);
            dirtyStates.remove(instanceId);
        }
    }

    private BlockFace attachedFace(BlockPlaceEvent event) {
        if (event == null || event.getBlockPlaced() == null || event.getBlockAgainst() == null) {
            return BlockFace.NORTH;
        }
        return event.getBlockPlaced().getFace(event.getBlockAgainst());
    }

    private boolean isHorizontal(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    private boolean usesFilter(SfxCargoComponentType type) {
        return type == SfxCargoComponentType.INPUT_NODE
                || type == SfxCargoComponentType.ADVANCED_INPUT_NODE
                || type == SfxCargoComponentType.ADVANCED_OUTPUT_NODE;
    }

    private boolean isAutoCrafter(SfxCargoComponentType type) {
        return type == SfxCargoComponentType.VANILLA_AUTO_CRAFTER
                || type == SfxCargoComponentType.ENHANCED_AUTO_CRAFTER
                || type == SfxCargoComponentType.ARMOR_AUTO_CRAFTER;
    }

    private SfxCargoComponentDefinition typeDefinition(SfxCargoComponentType type) {
        for (SfxCargoComponentDefinition definition : definitions.values()) {
            if (definition.type() == type) {
                return definition;
            }
        }
        return null;
    }

    private Location below(SfxBlockAnchorKey key) {
        World world = Bukkit.getWorld(key.worldId());
        return world == null ? null : new Location(world, key.x(), key.y() - 1, key.z());
    }

    private int compareAnchorKeys(SfxBlockAnchorKey left, SfxBlockAnchorKey right) {
        int byWorld = left.worldId().compareTo(right.worldId());
        if (byWorld != 0) {
            return byWorld;
        }
        int byX = Integer.compare(left.x(), right.x());
        if (byX != 0) {
            return byX;
        }
        int byY = Integer.compare(left.y(), right.y());
        if (byY != 0) {
            return byY;
        }
        return Integer.compare(left.z(), right.z());
    }

    private ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    private void dropPluginBlock(Block block, String typeId) {
        SfxBlockDrops.dropPluginBlock(block, items, typeId);
    }

    private void dropStack(Block block, ItemStack stack) {
        if (block == null || isEmpty(stack)) {
            return;
        }
        SfxBlockDrops.dropItem(block, stack.clone());
    }

    private record NodeRef(SfxBlockInstanceRecord instance, SfxCargoComponentDefinition definition, SfxCargoNodeState state, Endpoint endpoint) {
        NodeRef(SfxBlockInstanceRecord instance, SfxCargoComponentDefinition definition, SfxCargoNodeState state) {
            this(instance, definition, state, null);
        }

        NodeRef withEndpoint(Endpoint endpoint) {
            return new NodeRef(instance, definition, state, endpoint);
        }

        int priority() {
            return definition.type() == SfxCargoComponentType.ADVANCED_OUTPUT_NODE ? state.priority : 1;
        }
    }


    private Endpoint containerEndpoint(SfxVirtualContainer container) {
        return new Endpoint(container, false);
    }

    private Endpoint trashEndpoint() {
        return new Endpoint(null, true);
    }

    private final class Endpoint {
        private final SfxVirtualContainer container;
        private final boolean trash;

        private Endpoint(SfxVirtualContainer container, boolean trash) {
            this.container = container;
            this.trash = trash;
        }


        int capacityFor(ItemStack stack, boolean smartFill) {
            if (trash) {
                return stack == null ? 0 : stack.getAmount();
            }
            return virtualContainers.capacityFor(container, stack, smartFill);
        }

        ItemStack insert(ItemStack stack, boolean smartFill) {
            if (trash || isEmpty(stack)) {
                return null;
            }
            return virtualContainers.insert(container, stack, smartFill);
        }
    }
}
