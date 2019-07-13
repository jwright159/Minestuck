package com.mraof.minestuck.inventory;

import com.mraof.minestuck.alchemy.AlchemyRecipes;
import com.mraof.minestuck.item.MinestuckItems;
import com.mraof.minestuck.tileentity.MiniPunchDesignixTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

public class ContainerMiniPunchDesignix extends Container
{
	
	private static final int designixInputX = 44;
	private static final int designixInputY = 26;
	private static final int designixCardsX = 44;
	private static final int designixCardsY = 50;
	private static final int designixOutputX = 116;
	private static final int designixOutputY = 37;
	
	public MiniPunchDesignixTileEntity tileEntity;
	private int progress;
	
	public ContainerMiniPunchDesignix(PlayerInventory inventoryPlayer, MiniPunchDesignixTileEntity te)
	{
		tileEntity = te;
		
		addSlot(new Slot(tileEntity, 0, designixInputX, designixInputY));
		addSlot(new SlotInput(tileEntity, 1, designixCardsX, designixCardsY, MinestuckItems.CAPTCHA_CARD));
		addSlot(new SlotOutput(tileEntity, 2, designixOutputX, designixOutputY));
		
		bindPlayerInventory(inventoryPlayer);
	}
	
	@Override
	public boolean canInteractWith(EntityPlayer player)
	{
		return tileEntity.isUsableByPlayer(player);
	}
	
	protected void bindPlayerInventory(InventoryPlayer inventoryPlayer)
	{
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 9; j++)
				addSlot(new Slot(inventoryPlayer, j + i * 9 + 9,
						8 + j * 18, 84 + i * 18));
		
		for (int i = 0; i < 9; i++)
			addSlot(new Slot(inventoryPlayer, i, 8 + i * 18, 142));
	}
	
	@Nonnull
	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int slotNumber)
	{
		ItemStack itemstack = ItemStack.EMPTY;
		Slot slot = this.inventorySlots.get(slotNumber);
		int allSlots = this.inventorySlots.size();
		
		if (slot != null && slot.getHasStack())
		{
			ItemStack itemstackOrig = slot.getStack();
			itemstack = itemstackOrig.copy();
			boolean result = false;
			
			
			if(slotNumber <= 2)
			{
				//if it's a machine slot
				result = mergeItemStack(itemstackOrig, 3, allSlots, false);
			} else if(slotNumber > 2)
			{
				//if it's an inventory slot with valid contents
				if(itemstackOrig.getItem() == MinestuckItems.CAPTCHA_CARD && (!AlchemyRecipes.hasDecodedItem(itemstackOrig) || AlchemyRecipes.isPunchedCard(itemstackOrig)))
					result = mergeItemStack(itemstackOrig, 1, 2, false);
				else result = mergeItemStack(itemstackOrig, 0, 1, false);
			}
			
			if(!result)
				return ItemStack.EMPTY;
			
			if(!itemstackOrig.isEmpty())
				slot.onSlotChanged();
		}
		
		return itemstack;
	}
	
	@Override
	public void detectAndSendChanges()
	{
		if(this.progress != tileEntity.progress && tileEntity.progress != 0)
			for(IContainerListener listener : listeners)
				listener.sendWindowProperty(this, 0, tileEntity.progress);	//The server should update and send the progress bar to the client because client and server ticks aren't synchronized
		this.progress = tileEntity.progress;
	}
	@Override
	public void updateProgressBar(int par1, int par2) 
	{
		if(par1 == 0)
		{
			tileEntity.progress = par2;
		}
	}
}