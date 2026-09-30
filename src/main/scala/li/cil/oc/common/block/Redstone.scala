package li.cil.oc.common.block

import li.cil.oc.common.blockentity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.state.BlockState


class Redstone(props: Properties) extends RedstoneAware(props) with traits.Tickable {

  // ----------------------------------------------------------------------- //

  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Redstone(pos, state)

  override def getBlockEntityType = blockentity.BlockEntityTypes.REDSTONE_IO.get()
}
