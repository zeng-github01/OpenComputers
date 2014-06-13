package li.cil.oc.common.tileentity

import li.cil.oc.api
import li.cil.oc.Settings
import li.cil.oc.api.network.{Arguments, Context, Callback, Visibility}
import net.minecraft.block.{BlockFluid, Block}
import net.minecraftforge.fluids.FluidRegistry

class Geolyzer extends traits.Environment {
  val node = api.Network.newNode(this, Visibility.Network).
    withComponent("geolyzer").
    withConnector().
    create()

  override def canUpdate = false

  @Callback(doc = """function(x:number, z:number[, ignoreReplaceable:boolean) -- Analyzes the density of the column at the specified relative coordinates.""")
  def scan(computer: Context, args: Arguments): Array[AnyRef] = {
    val rx = args.checkInteger(0)
    val rz = args.checkInteger(1)
    val includeReplaceable = !(args.count > 2 && args.checkBoolean(2))
    if (math.abs(rx) > Settings.get.geolyzerRange || math.abs(rz) > Settings.get.geolyzerRange) {
      throw new IllegalArgumentException("location out of bounds")
    }
    val bx = x + rx
    val bz = z + rz

    if (!node.tryChangeBuffer(-Settings.get.geolyzerScanCost))
      return result(Unit, "not enough energy")

    val values = new Array[Float](64)
    for (ry <- 0 until values.length) {
      val by = y + ry - 32
      val blockId = world.getBlockId(bx, by, bz)
      if (blockId > 0 && !world.isAirBlock(bx, by, bz)) {
        val block = Block.blocksList(blockId)
        if (block != null && (includeReplaceable || isFluid(block) || !block.isBlockReplaceable(world, x, y, z))) {
          values(ry) = block.getBlockHardness(world, bx, by, bz)
        }
      }
    }

    result(values)
  }

  private def isFluid(block: Block) = FluidRegistry.lookupFluidForBlock(block) != null || block.isInstanceOf[BlockFluid]
}
