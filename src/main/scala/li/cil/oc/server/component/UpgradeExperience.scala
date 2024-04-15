package li.cil.oc.server.component

import java.util
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.{BlockPosition, UpgradeExperience}
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.item.EntityXPOrb
import net.minecraft.init.Items
import net.minecraft.nbt.NBTTagCompound

import scala.collection.convert.WrapAsJava._
import scala.collection.convert.WrapAsScala._

class UpgradeExperience(val host: EnvironmentHost with internal.Agent) extends AbstractManagedEnvironment with traits.WorldAware with DeviceInfo {
  final val MaxLevel = 30

  override val node = api.Network.newNode(this, Visibility.Network).
    withComponent("experience").
    withConnector(30 * Settings.get.bufferPerLevel).
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Knowledge database",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "ERSO (Event Recorder and Self-Optimizer)",
    DeviceAttribute.Capacity -> "30"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  override def position: BlockPosition = BlockPosition(host)

  var experience = 0.0

  var level = 0

  def xpForNextLevel: Double = UpgradeExperience.xpForLevel(level + 1)

  def addExperience(value: Double) {
    if (level < MaxLevel) {
      experience = experience + value
      if (experience >= xpForNextLevel) {
        updateXpInfo()
      }
      val world = this.host.world
      val pos = this.host.player.getPosition
      val orb = new EntityXPOrb(world, pos.getX.toDouble + 0.5D, pos.getY.toDouble + 0.5D, pos.getZ.toDouble + 0.5D, value.toInt)
      this.host.player.xpCooldown = 0
      orb.onCollideWithPlayer(this.host.player)
    }
  }

  def updateXpInfo() {
    // xp(level) = base + (level * const) ^ exp
    // pow(xp(level) - base, 1/exp) / const = level
    val oldLevel = level
    level = UpgradeExperience.calculateLevelFromExperience(experience)
    if (node != null) {
      if (level != oldLevel) {
        updateClient()
      }
      node.setLocalBufferSize(Settings.get.bufferPerLevel * level)
    }
  }

  @Callback(direct = true, doc = """function():number -- The current level of experience stored in this experience upgrade.""")
  def level(context: Context, args: Arguments): Array[AnyRef] =
    result(UpgradeExperience.calculateExperienceLevel(level, experience))

  @Callback(doc = """function():boolean -- Tries to consume an enchanted item to add experience to the upgrade.""")
  def consume(context: Context, args: Arguments): Array[AnyRef] = {
    if (level >= MaxLevel) {
      return result(Unit, "max level")
    }
    val stack = host.mainInventory.getStackInSlot(host.selectedSlot)
    if (stack.isEmpty) {
      return result(Unit, "no item")
    }
    var xp = 0
    if (stack.getItem == Items.EXPERIENCE_BOTTLE) {
      xp += 3 + host.world.rand.nextInt(5) + host.world.rand.nextInt(5)
    }
    else {
      for ((enchantment, level) <- EnchantmentHelper.getEnchantments(stack)) {
        if (enchantment != null) {
          xp += enchantment.getMinEnchantability(level)
        }
      }
      if (xp <= 0) {
        return result(Unit, "could not extract experience from item")
      }
    }
    val consumed = host.mainInventory().decrStackSize(host.selectedSlot, 1)
    if (consumed.isEmpty) {
      return result(Unit, "could not consume item")
    }
    addExperience(xp * Settings.get.constantXpGrowth)
    result(true)
  }

  @Callback(doc = """function():number -- suck experience from nearby. return the amount of sucked experience""")
  def suck(context: Context, args: Arguments): Array[AnyRef] = {
    val nearBounds = position.bounds
    val farBounds = nearBounds.offset(Settings.get.suckXpRange, Settings.get.suckXpRange, Settings.get.suckXpRange)
    val bounds = nearBounds.union(farBounds)
    var gained = 0D
    entitiesInBounds[EntityXPOrb](classOf[EntityXPOrb], bounds).foreach(orb => {

      if (!orb.isDead && orb.xpValue > 0) {
        val copy = experience.toDouble
        addExperience(orb.xpValue * Settings.get.robotSuckXpRate)
        val added = (experience.toDouble - copy) / Settings.get.robotSuckXpRate
        gained += added
        orb.xpValue = (orb.xpValue - added.toInt)
        if (orb.xpValue <= 0) {
          orb.setDead()
        }
      }
    })
    result(gained)
  }

  private def updateClient() = host match {
    case robot: internal.Robot => robot.synchronizeSlot(robot.componentSlot(node.address))
    case _ =>
  }

  override def save(nbt: NBTTagCompound) {
    super.save(nbt)
    UpgradeExperience.setExperience(nbt, experience)
  }

  override def load(nbt: NBTTagCompound) {
    super.load(nbt)
    experience = UpgradeExperience.getExperience(nbt)
    updateXpInfo()
  }
}
