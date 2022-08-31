package space.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.Waterloggable;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

public class LycophyteBlock extends Block implements Fertilizable, Waterloggable
{
	private static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
	private final boolean isTop;

	public LycophyteBlock(AbstractBlock.Settings settings, boolean isTop)
	{
		super(settings);
		this.isTop = isTop;
		this.setDefaultState(this.stateManager.getDefaultState().with(WATERLOGGED, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
	{
		builder.add(WATERLOGGED);
	}

	@Override
	public FluidState getFluidState(BlockState state)
	{
		if(state.get(WATERLOGGED).booleanValue())
			return Fluids.WATER.getStill(false);

		return super.getFluidState(state);
	}
	
	@Override
	public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos)
	{
		Block block = world.getBlockState(pos.down()).getBlock();
		return block == StarflightBlocks.FERRIC_SAND || block == StarflightBlocks.ARES_MOSS_BLOCK || block == StarflightBlocks.LYCOPHYTE_TOP || block == StarflightBlocks.LYCOPHYTE_STEM;
	}

	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos)
	{
		BlockState blockState = state;

		if(isTop && neighborState.getBlock() instanceof LycophyteBlock && neighborPos.equals(pos.up()))
			blockState = StarflightBlocks.LYCOPHYTE_STEM.getDefaultState();

		return blockState;
	}

	@Override
	public boolean isFertilizable(BlockView var1, BlockPos var2, BlockState var3, boolean var4)
	{
		return true;
	}

	@Override
	public boolean canGrow(World world, Random random, BlockPos pos, BlockState blockState)
	{
		return true;
	}

	@Override
	public void grow(ServerWorld world, Random random, BlockPos pos, BlockState blockState)
	{
		while(world.getBlockState(pos).getBlock() == StarflightBlocks.LYCOPHYTE_STEM)
			pos = pos.up();
		
		pos = pos.up();
		world.setBlockState(pos, StarflightBlocks.LYCOPHYTE_TOP.getDefaultState());
		world.setBlockState(pos.down(), StarflightBlocks.LYCOPHYTE_STEM.getDefaultState());
	}

	@Override
	public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state)
	{
		return new ItemStack(StarflightBlocks.LYCOPHYTE_TOP);
	}
}