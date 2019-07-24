package com.mraof.minestuck.item.weapon;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.IItemTier;
import net.minecraft.item.ItemStack;

/**
 * Created by mraof on 2017 January 18 at 7:15 PM.
 */
public class SordItem extends WeaponItem
{
	
	public SordItem(IItemTier tier, int attackDamageIn, float attackSpeedIn, float efficiency, Properties builder)
	{
		super(tier, attackDamageIn, attackSpeedIn, efficiency, builder);
	}
	
	@Override
	public boolean hitEntity(ItemStack itemStack, LivingEntity target, LivingEntity attacker)
	{
		if (attacker.getRNG().nextFloat() < .25)
		{
			ItemEntity sord = new ItemEntity(attacker.world, attacker.posX, attacker.posY, attacker.posZ, itemStack.copy());
			sord.getItem().setCount(1);
			sord.setPickupDelay(40);
			attacker.world.addEntity(sord);
			itemStack.shrink(1);
		}
		return super.hitEntity(itemStack, target, attacker);
	}
}
