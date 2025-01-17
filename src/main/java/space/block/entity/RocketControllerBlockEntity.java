package space.block.entity;

import java.util.ArrayList;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import space.StarflightMod;
import space.block.RocketThrusterBlock;
import space.block.StarflightBlocks;
import space.planet.PlanetDimensionData;
import space.planet.PlanetList;
import space.util.BlockSearch;
import space.vessel.BlockMass;

public class RocketControllerBlockEntity extends BlockEntity
{
	private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(2, ItemStack.EMPTY);
	private ArrayList<PlayerEntity> viewingList = new ArrayList<PlayerEntity>();
	private ArrayList<BlockPos> positionList = new ArrayList<BlockPos>();
	private String targetName = "null";
	private double mass = 0.0;
	private double thrust = 0.0;
	private double thrustVacuum = 0.0;
	private double averageVE = 0.0;
	private double averageVEVacuum = 0.0;
	private double hydrogen = 0.0;
	private double hydrogenCapacity = 0.0;
	private double oxygen = 0.0;
	private double oxygenCapacity = 0.0;
	private double deltaV = 0.0;
	private double deltaVCapacity = 0.0;
	private double requiredDeltaV1 = 0.0;
	private double requiredDeltaV2 = 0.0;
	private boolean hasCard = false;
	
	public RocketControllerBlockEntity(BlockPos pos, BlockState state)
	{
		super(StarflightBlocks.ROCKET_CONTROLLER_BLOCK_ENTITY, pos, state);
	}
	
	/**
	 * Compile a list of blocks to be included in vehicle construction and determine various parameters.
	 */
	public void runScan()
	{
		// Clear all variables.
		targetName = "null";
		mass = 0.0;
		thrust = 0.0;
		thrustVacuum = 0.0;
		averageVE = 0.0;
		averageVEVacuum = 0.0;
		hydrogen = 0.0;
		hydrogenCapacity = 0.0;
		oxygen = 0.0;
		oxygenCapacity = 0.0;
		deltaV = 0.0;
		deltaVCapacity = 0.0;
		requiredDeltaV1 = 0.0;
		requiredDeltaV2 = 0.0;
		positionList.clear();
		
		// Detect blocks to be included in the craft construction.
        ArrayList<BlockPos> positionList = new ArrayList<BlockPos>();
        BlockSearch.movingCraftSearch(world, getPos(), positionList, BlockSearch.MAX_VOLUME);
		
        if(positionList.size() >= BlockSearch.MAX_VOLUME)
        {
        	positionList.clear();
        	return;
        }
        
        // Update the rocket's mass, fuel supply, and thrust data.
        double massFlowSum = 0;
        
        for(BlockPos pos : positionList)
        {
        	boolean redstone = world.isReceivingRedstonePower(pos);
        	mass += BlockMass.getMass(world, pos);
        	BlockEntity blockEntity = world.getBlockEntity(pos);
        	
        	if(blockEntity != null)
        	{
        		if(blockEntity instanceof HydrogenTankBlockEntity)
        		{
        			HydrogenTankBlockEntity hydrogenTank = (HydrogenTankBlockEntity) blockEntity;
        			
        			if(!redstone)
        			{
        				hydrogen += hydrogenTank.getStoredFluid();
            			hydrogenCapacity += hydrogenTank.getStorageCapacity();
        			}
        			
        			mass += hydrogenTank.getStoredFluid();
        		}
        		else if(blockEntity instanceof OxygenTankBlockEntity)
        		{
        			OxygenTankBlockEntity oxygenTank = (OxygenTankBlockEntity) blockEntity;

        			if(!redstone)
        			{
        				oxygen += oxygenTank.getStoredFluid();
        				oxygenCapacity += oxygenTank.getStorageCapacity();
        			}
        			
        			mass += oxygenTank.getStoredFluid();
        		}
        	}
        	else if(world.getBlockState(pos).getBlock() instanceof RocketThrusterBlock && !redstone)
        	{
        		PlanetDimensionData data = PlanetList.getDimensionDataForWorld(world);
        		double pressure = 0.0;
        		
        		if(data != null && !data.isOrbit())
        			pressure = data.getPressure();
        		
        		thrust += ((RocketThrusterBlock) world.getBlockState(pos).getBlock()).getThrust(pressure);
        		thrustVacuum += ((RocketThrusterBlock) world.getBlockState(pos).getBlock()).getThrust(0.0);
        		massFlowSum += ((RocketThrusterBlock) world.getBlockState(pos).getBlock()).getMassFlow();
        	}
        }
        
        averageVE = 9.80665 * (thrust / massFlowSum);
        averageVEVacuum = 9.80665 * (thrustVacuum / massFlowSum);
        deltaV = availableDV(mass, hydrogen, oxygen, averageVEVacuum);
        deltaVCapacity = availableDV(mass + (hydrogenCapacity - hydrogen) + (oxygenCapacity - oxygen), hydrogenCapacity, oxygenCapacity, averageVEVacuum);
        
        // Run delta-V calculations.
        //runDeltaVCalculations();
	}
	
	private double availableDV(double initialMass, double hydrogen, double oxygen, double averageVE)
	{
		double fuelMass = Math.min(oxygen + (oxygen / 8.0), hydrogen + (hydrogen * 8.0));
		double finalMass = initialMass - fuelMass;
		return averageVE * Math.log(initialMass / finalMass);
	}
	
	/**
	 * Update the destination planet to what is found on a navigation card and run delta-v calculations.
	 */
	/*public void runDeltaVCalculations()
	{
		PlanetDimensionData data = PlanetList.getDimensionDataForWorld(world);
		Planet currentPlanet = data.getPlanet();
        
        if(data.isOrbit())
        {
        	requiredDeltaV1 = currentPlanet.dVOrbitToSurface();
        	
	        ItemStack stack = inventory.get(0);
	        
	        if(stack != null && stack.getItem() instanceof NavigationCardItem)
	        {
	        	NbtCompound nbt = stack.getNbt();
	        	
	        	if(nbt != null && nbt.contains("planet"))
	        		targetName = nbt.getString("planet");
	        	else
	        		targetName = "null";
	        }
	        else
	        	targetName = "null";
	        
	        Planet target = PlanetList.getByName(targetName);
	        requiredDeltaV2 = target != null ? currentPlanet.dVToPlanet(target) : 0;
        }
        else
        {
        	targetName = "null";
        	requiredDeltaV1 = currentPlanet.dVSurfaceToOrbit();
        	requiredDeltaV2 = 0;
        }
	}*/
	
	public static void receiveButtonPress(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buffer, PacketSender sender)
	{
		RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, new Identifier(buffer.readString()));
		BlockPos position = buffer.readBlockPos();
		int buttonID = buffer.readInt();
		
		server.execute(() -> {
			ServerWorld world = server.getWorld(worldKey);
			BlockEntity blockEntity = world.getBlockEntity(position);
			
			if(blockEntity != null && blockEntity instanceof RocketControllerBlockEntity)
			{
				RocketControllerBlockEntity rocketController = (RocketControllerBlockEntity) blockEntity;
				
				
				
				
				/*ItemStack navigationCard = rocketController.getStack(0);
				ItemStack arrivalCard = rocketController.getStack(1);
				rocketController.runScan();
				
				// Button 0 performs a scan, so no further action is taken.
				// Button 1 initiates a launch to orbit or a landing from orbit.
				// Button 2 Initiates a transfer to orbit around the planet specified by the navigation card.
				if(buttonID == 1 || buttonID == 2)
				{
					PlanetDimensionData data = PlanetList.getDimensionDataForWorld(world);
					RegistryKey<World> nextWorld = data.isOrbit() ? PlanetList.getPlanetWorldKey(data.getPlanet()) : PlanetList.getParkingOrbitWorldKey(data.getPlanet());
					
					if(buttonID == 2 && !navigationCard.isEmpty() && navigationCard.hasNbt())
					{
						String planetName = navigationCard.getNbt().getString("planet");
						nextWorld = PlanetList.getParkingOrbitWorldKey(PlanetList.getByName(planetName));
					}
					
					// Detect blocks to be included in the craft construction.
			        ArrayList<BlockPos> positionList = new ArrayList<BlockPos>();
			        BlockSearch.movingCraftSearch(world, position, positionList, BlockSearch.MAX_VOLUME);
			        
			        if(!positionList.isEmpty() && positionList.size() < BlockSearch.MAX_VOLUME)
			        {
			        	double requiredDeltaV = buttonID == 1 ? rocketController.requiredDeltaV1 : rocketController.requiredDeltaV2;
			        	double finalMass = rocketController.mass * Math.exp(-requiredDeltaV / rocketController.averageVEVacuum);
			        	BlockPos arrivalPos = new BlockPos(-9999, -9999, -9999);
			        	int arrivalDirection = world.getBlockState(position).get(RocketControllerBlock.FACING).getOpposite().getHorizontal();
			        	
			        	if(!arrivalCard.isEmpty() && arrivalCard.hasNbt())
			        	{
			        		NbtCompound nbt = arrivalCard.getNbt();
			        		arrivalPos = new BlockPos(nbt.getInt("x"), -9999, nbt.getInt("z"));
			        		arrivalDirection = nbt.getInt("d");
			        	}
			        	
						RocketEntity entity = new RocketEntity(world, world.getBlockState(position).get(RocketControllerBlock.FACING), positionList, arrivalPos, arrivalDirection);
	
						if (!entity.isRemoved())
							world.spawnEntity(entity);
			        }
				}*/
				
				rocketController.sendDisplayData(player);
			}
		});
	}
	
	public static void tick(World world, BlockPos pos, BlockState state, RocketControllerBlockEntity blockEntity)
	{
		// Recalculate travel delta-v if a navigation card change is detected.
		ItemStack stack = blockEntity.inventory.get(0);
		
		if(stack.isEmpty() == blockEntity.hasCard)
		{
			//blockEntity.runDeltaVCalculations();
			
			for(PlayerEntity player : blockEntity.viewingList)
				blockEntity.sendDisplayData(world.getServer().getPlayerManager().getPlayer(player.getUuid()));
			
			blockEntity.hasCard = !stack.isEmpty();
		}
	}

	@Override
	public void readNbt(NbtCompound nbt)
	{
		super.readNbt(nbt);
		Inventories.readNbt(nbt, this.inventory);
		targetName = nbt.getString("targetName");
		mass = nbt.getDouble("mass");
		thrust = nbt.getDouble("thrust");
		thrustVacuum = nbt.getDouble("thrustVacuum");
		averageVE = nbt.getDouble("averageVE");
		averageVEVacuum = nbt.getDouble("averageVEVacuum");
		hydrogen = nbt.getDouble("hydrogen");
		hydrogenCapacity = nbt.getDouble("hydrogenCapacity");
		oxygen = nbt.getDouble("oxygen");
		oxygenCapacity = nbt.getDouble("oxygenCapacity");
		deltaV = nbt.getDouble("deltaV");
		deltaVCapacity = nbt.getDouble("deltaVCapacity");
		requiredDeltaV1 = nbt.getDouble("requiredDeltaV1");
		requiredDeltaV2 = nbt.getDouble("requiredDeltaV2");
		hasCard = nbt.getBoolean("hasCard");
	}

	@Override
	public void writeNbt(NbtCompound nbt)
	{
		super.writeNbt(nbt);
		Inventories.writeNbt(nbt, this.inventory);
		nbt.putString("targetName", targetName);
		nbt.putDouble("mass", mass);
		nbt.putDouble("thrust", thrust);
		nbt.putDouble("thrustVacuum", thrustVacuum);
		nbt.putDouble("averageVE", averageVE);
		nbt.putDouble("averageVEVacuum", averageVEVacuum);
		nbt.putDouble("hydrogen", hydrogen);
		nbt.putDouble("hydrogenCapacity", hydrogenCapacity);
		nbt.putDouble("oxygen", oxygen);
		nbt.putDouble("oxygenCapacity", oxygenCapacity);
		nbt.putDouble("deltaV", deltaV);
		nbt.putDouble("deltaVCapacity", deltaVCapacity);
		nbt.putDouble("requiredDeltaV1", requiredDeltaV1);
		nbt.putDouble("requiredDeltaV2", requiredDeltaV2);
		nbt.putBoolean("hasCard", hasCard);
	}
	
	public void sendDisplayData(ServerPlayerEntity player)
	{
		PacketByteBuf buffer = PacketByteBufs.create();
		buffer.writeString(getWorld().getRegistryKey().getValue().toString());
		buffer.writeBlockPos(getPos());
		buffer.writeString(targetName);
		buffer.writeDouble(mass);
		buffer.writeDouble(thrust);
		buffer.writeDouble(hydrogen);
		buffer.writeDouble(hydrogenCapacity);
		buffer.writeDouble(oxygen);
		buffer.writeDouble(oxygenCapacity);
		buffer.writeDouble(deltaV);
		buffer.writeDouble(deltaVCapacity);
		buffer.writeDouble(requiredDeltaV1);
		buffer.writeDouble(requiredDeltaV2);
		ServerPlayNetworking.send(player, new Identifier(StarflightMod.MOD_ID, "rocket_controller_data"), buffer);
	}
}