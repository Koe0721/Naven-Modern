package com.heypixel.heypixelmod.obsoverlay.modules.impl.combat;

import com.heypixel.heypixelmod.obsoverlay.events.api.EventTarget;
import com.heypixel.heypixelmod.obsoverlay.events.impl.EventHandlePacket;
import com.heypixel.heypixelmod.obsoverlay.modules.Category;
import com.heypixel.heypixelmod.obsoverlay.modules.Module;
import com.heypixel.heypixelmod.obsoverlay.modules.ModuleInfo;
import com.heypixel.heypixelmod.obsoverlay.utils.ChatUtils;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.item.EnderpearlItem;

/**
 * 垂直击退 0logs Reduce Velocity
 * 基于1.8.9 GrimAC reduce算法 通过极小浮点系数削减Y轴速度
 * 保持客户端-服务器simulation同步 不被GrimAC检测
 */
@ModuleInfo(
        name = "Velocity",
        description = "Vertical knockback reduction - 0logs.",
        category = Category.MOVEMENT
)
public class Velocity extends Module {

   // 唯一允许的功能: 日志
   public BooleanValue logging = ValueBuilder.create(this, "Logging")
           .setDefaultBooleanValue(false)
           .build().getBooleanValue();

   // 硬编码的垂直击退削减系数 (reduce velocity)
   // Y轴乘以此系数 0.5 = 50% 削减
   private static final double VERTICAL_REDUCE_FACTOR = 0.5D;

   @Override
   public void onEnable() {
   }

   @Override
   public void onDisable() {
   }

   /**
    * 核心处理: 接收到velocity包时削减Y轴
    * 不cancel包 只修改packet内容 保证simulation同步
    */
   @EventTarget
   public void onPacket(EventHandlePacket e) {
      if (mc.player == null || mc.getConnection() == null) {
         return;
      }

      Packet<?> packet = e.getPacket();

      if (!(packet instanceof ClientboundSetEntityMotionPacket velocityPacket)) {
         return;
      }

      // 只处理发给自己的velocity包
      if (velocityPacket.getId() != mc.player.getId()) {
         return;
      }

      // 排除：吃东西、掉下来时、手持末地珍珠
      if (mc.player.isUsingItem() 
              || velocityPacket.getYa() < 0 
              || mc.player.getMainHandItem().getItem() instanceof EnderpearlItem) {
         return;
      }

      // 获取原始速度值 (packet中存储的是 * 8000的整数)
      int rawYa = velocityPacket.getYa();
      
      // 转换为实际Y速度
      double originalY = rawYa / 8000.0D;
      
      // 应用reduce factor
      double reducedY = originalY * VERTICAL_REDUCE_FACTOR;
      
      // 转换回packet格式 (整数 * 8000)
      int newRawYa = (int)(reducedY * 8000.0D);

      // 通过反射修改packet的Y值 (因为packet字段可能是final)
      try {
         java.lang.reflect.Field yaField = ClientboundSetEntityMotionPacket.class.getDeclaredField("ya");
         yaField.setAccessible(true);
         yaField.setInt(velocityPacket, newRawYa);
         
         log("Y reduced: " + String.format("%.4f", originalY)
                 + " -> " + String.format("%.4f", reducedY));
         
      } catch (NoSuchFieldException | IllegalAccessException ex) {
         // 如果反射失败 尝试另一个方案: cancel + 手动应用
         log("Reflect failed!");
         
         double velocityX = velocityPacket.getXa() / 8000.0D;
         double velocityZ = velocityPacket.getZa() / 8000.0D;
         
         // 只削减Y, X和Z保持原值
         mc.player.setDeltaMovement(velocityX, reducedY, velocityZ);
         e.setCancelled(true);
      }
   }

   private void log(String message) {
      if (logging.getCurrentValue()) {
         ChatUtils.addChatMessage("[Velocity] " + message);
      }
   }
}
