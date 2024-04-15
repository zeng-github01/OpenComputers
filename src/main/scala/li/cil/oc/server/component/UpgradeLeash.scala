package li.cil.oc.server.component

import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.{DeviceAttribute, DeviceClass}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{Node, Visibility}
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.api.{Network, internal}
import li.cil.oc.common.EventHandler
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.{Constants, OpenComputers}
import net.minecraft.entity.{Entity, EntityLiving}
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.nbt.{NBTTagCompound, NBTTagString}
import net.minecraftforge.common.util.Constants.NBT

import java.util
import java.util.UUID
import scala.collection.convert.WrapAsJava._
import scala.collection.convert.WrapAsScala._
import scala.collection.mutable
import scala.util.control.Breaks._

class UpgradeLeash(val host: Entity with internal.Drone) extends AbstractManagedEnvironment with traits.WorldAware with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("leash").
    create()

  final val MaxLeashedEntities = 8

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Leash",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "FlockControl (FC-3LS)",
    DeviceAttribute.Capacity -> MaxLeashedEntities.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  val leashedEntities = mutable.Set.empty[UUID]

  override def position = BlockPosition(host.getPosition)

  @Callback(doc = """function(side:number):boolean -- Tries to put an entity on the specified side of the device onto a leash.""")
  def leash(context: Context, args: Arguments): Array[AnyRef] = {
    if (leashedEntities.size >= MaxLeashedEntities) return result(Unit, "too many leashed entities")
    val side = args.checkSideAny(0)
    val nearBounds = position.bounds
    val farBounds = nearBounds.offset(side.getXOffset * 2.0, side.getYOffset * 2.0, side.getZOffset * 2.0)
    val bounds = nearBounds.union(farBounds)

    for (index <- 0 to host.mainInventory().getSizeInventory) {
      val stack = host.mainInventory().getStackInSlot(index)
      if (stack.getItem == Items.LEAD) {
        entitiesInBounds[EntityLiving](classOf[EntityLiving], bounds).find(_.canBeLeashedTo(fakePlayer)) match {
          case Some(entity) =>
            entity.setLeashHolder(host, true)
            leashedEntities += entity.getUniqueID
            stack.shrink(1)
            context.pause(0.1)
            result(true)
          case _ => result(Unit, "no unleashed entity")
        }
      }
    }
    result(Unit, "don't have any lead")
  }

  @Callback(doc = """function() -- Unleashes all currently leashed entities.""")
  def unleash(context: Context, args: Arguments): Array[AnyRef] = {
    unleashAll()
    null
  }

  override def onDisconnect(node: Node) {
    super.onDisconnect(node)
    if (node == this.node) {
      unleashAll()
    }
  }

  private def unleashAll() {
    entitiesInBounds(classOf[EntityLiving], position.bounds.grow(5, 5, 5)).foreach(entity => {
      if (leashedEntities.contains(entity.getUniqueID) && entity.getLeashHolder == host) {
        entity.clearLeashed(true, false)
        breakable {
          for (index <- 0 to host.mainInventory().getSizeInventory) {
            val itemStack = host.mainInventory().getStackInSlot(index)
            if (itemStack == ItemStack.EMPTY) {
              host.mainInventory().setInventorySlotContents(index, new ItemStack(Items.LEAD))
              break()
            }

            if (itemStack.getItem == Items.LEAD) {
              itemStack.grow(1)
              break()
            }
          }
        }
      }
    })
    leashedEntities.clear()
  }

  private final val LeashedEntitiesTag = "leashedEntities"

  override def load(nbt: NBTTagCompound) {
    super.load(nbt)
    leashedEntities ++= nbt.getTagList(LeashedEntitiesTag, NBT.TAG_STRING).
      map((s: NBTTagString) => UUID.fromString(s.getString))
    // Re-acquire leashed entities. Need to do this manually because leashed
    // entities only remember their leashee if it's an EntityLivingBase...
    EventHandler.scheduleServer(() => {
      val foundEntities = mutable.Set.empty[UUID]
      entitiesInBounds(classOf[EntityLiving], position.bounds.grow(5, 5, 5)).foreach(entity => {
        if (leashedEntities.contains(entity.getUniqueID)) {
          entity.setLeashHolder(host, true)
          foundEntities += entity.getUniqueID
        }
      })
      val missing = leashedEntities.diff(foundEntities)
      if (missing.nonEmpty) {
        OpenComputers.log.info(s"Could not find ${missing.size} leashed entities after loading!")
        leashedEntities --= missing
      }
    })
  }

  override def save(nbt: NBTTagCompound) {
    super.save(nbt)
    nbt.setNewTagList(LeashedEntitiesTag, leashedEntities.map(_.toString))
  }
}
