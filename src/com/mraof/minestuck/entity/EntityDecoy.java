package com.mraof.minestuck.entity;

import com.mraof.minestuck.editmode.ServerEditHandler;
import com.mraof.minestuck.util.Debug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Constructor;
import java.util.UUID;

public class EntityDecoy extends EntityLiving {

	private static final DataParameter<String> USERNAME = EntityDataManager.createKey(EntityDecoy.class, DataSerializers.STRING);
	private static final DataParameter<String> PLAYER_UUID = EntityDataManager.createKey(EntityDecoy.class, DataSerializers.STRING);
	private static final DataParameter<Float> ROTATION_YAW_HEAD = EntityDataManager.createKey(EntityDecoy.class, DataSerializers.FLOAT);
	private static final DataParameter<Boolean> FLYING = EntityDataManager.createKey(EntityDecoy.class, DataSerializers.BOOLEAN);

	public boolean isFlying;
	public GameType gameType;
	public String username;
	public UUID uuid;
	private FoodStats foodStats;
	private NBTTagCompound foodStatsNBT;
	public NBTTagCompound capabilities = new NBTTagCompound();

	public boolean markedForDespawn;
	boolean init;
	double originX, originY, originZ;
	DecoyPlayer player;

	ResourceLocation locationSkin;
	ResourceLocation locationCape;
	ThreadDownloadImageData downloadImageSkin;
	ThreadDownloadImageData downloadImageCape;
	public InventoryPlayer inventory;

	public EntityDecoy(World world)
	{
		super(world);
		inventory = new InventoryPlayer(null);
		if(!world.isRemote)	//If not spawned the way it should
			markedForDespawn = true;
	}

	public EntityDecoy(WorldServer world, EntityPlayerMP player)
	{
		super(world);
		this.setEntityBoundingBox(player.getEntityBoundingBox());
		height = player.height;
		this.player = new DecoyPlayer(world, this, player);
		for(String key : player.getEntityData().getKeySet())
			this.player.getEntityData().setTag(key, player.getEntityData().getTag(key).copy());
		this.posX = player.posX;
		originX = posX;
		this.chunkCoordX = player.chunkCoordX;
		this.posY = player.posY;
		originY = posY;
		this.chunkCoordY = player.chunkCoordY;
		this.posZ = player.posZ;
		originZ = posZ;
		this.chunkCoordZ = player.chunkCoordZ;
		this.rotationPitch = player.rotationPitch;
		this.rotationYaw = player.rotationYaw;
		this.rotationYawHead = player.rotationYawHead;
		this.renderYawOffset = player.renderYawOffset;
		this.gameType = player.interactionManager.getGameType();

		uuid = player.getUniqueID();

		for(PotionEffect effect : player.getActivePotionEffects())
			addPotionEffect(effect);

		initInventory(player);
		player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getModifiers().forEach(attributeModifier ->
				this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).applyModifier(attributeModifier));
		player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).getModifiers().forEach(attributeModifier ->
				this.player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).applyModifier(attributeModifier));
		this.setHealth(player.getHealth());
		username = player.getName();
		isFlying = player.capabilities.isFlying;
		player.capabilities.writeCapabilitiesToNBT(this.capabilities);
		foodStatsNBT = new NBTTagCompound();
		player.getFoodStats().writeNBT(foodStatsNBT);
		initFoodStats(player);
		dataManager.set(USERNAME, username);
		dataManager.set(PLAYER_UUID, uuid.toString());
		dataManager.set(ROTATION_YAW_HEAD, this.rotationYawHead);	//Due to rotationYawHead didn't update correctly
		dataManager.set(FLYING, isFlying);

		if(player.isRiding())
			startRiding(player.getRidingEntity());
		for(Entity p : player.getPassengers())
			p.startRiding(player);
	}

	private void initInventory(EntityPlayerMP player)
	{
		inventory = new InventoryPlayer(this.player);
		this.player.inventory = inventory;
		if(player.inventory.getClass() != InventoryPlayer.class)	//Custom inventory class
		{
			Class<? extends InventoryPlayer> c = player.inventory.getClass();
			try
			{
				Constructor<? extends InventoryPlayer> constructor = c.getConstructor(EntityPlayer.class);
				inventory = constructor.newInstance(this.player);
				this.player.inventory = inventory;
			} catch(Exception e)
			{
				throw new IllegalStateException("The custom inventory class \""+c.getName()+"\" is not supported.");
			}
		}

		inventory.copyInventory(player.inventory);
	}

	private void initFoodStats(EntityPlayerMP sourcePlayer)
	{
		try
		{
			try
			{
				foodStats = new FoodStats();
			} catch(NoSuchMethodError e)
			{
				Debug.info("Custom constructor detected for FoodStats. Trying with player as parameter...");
				try
				{
					foodStats = FoodStats.class.getConstructor(EntityPlayer.class).newInstance(player);
				}
				catch(NoSuchMethodException ex)
				{
					throw new NoSuchMethodException("Found no known constructor for net.minecraft.util.FoodStats.");
				}
			}
			foodStats.readNBT(foodStatsNBT);	//Exact copy of food stack
		} catch(Exception e)
		{
			foodStats = null;
			Debug.logger.error("Couldn't initiate food stats for player decoy. Proceeding to not simulate food stats.", e);
			sourcePlayer.sendMessage(new TextComponentString("An issue came up while creating the decoy. More info in the server logs."));
		}
	}

	public NBTTagCompound getFoodStatsNBT()
	{
		if(foodStats != null)
		{
			NBTTagCompound nbt = new NBTTagCompound();
			foodStats.writeNBT(nbt);
			return nbt;
		} else return foodStatsNBT;
	}

	@Override
	protected void entityInit()
	{
		super.entityInit();
		dataManager.register(USERNAME, "");
		dataManager.register(PLAYER_UUID, "");
		dataManager.register(ROTATION_YAW_HEAD, 0F);
		dataManager.register(FLYING, false);
	}

	@SideOnly(Side.CLIENT)
	protected void setupCustomSkin()
	{
		NetworkPlayerInfo info = Minecraft.getMinecraft().getConnection().getPlayerInfo(uuid);

		if(uuid != null && world.getPlayerEntityByUUID(uuid) instanceof AbstractClientPlayer)
		{
			AbstractClientPlayer player = ((AbstractClientPlayer) world.getPlayerEntityByUUID(uuid));
			locationSkin = player.getLocationSkin();
			locationCape = player.getLocationCape();
		}
	}

	public ThreadDownloadImageData getTextureSkin() {
		return downloadImageSkin;
	}

	public ThreadDownloadImageData getTextureCape() {
		return downloadImageCape;
	}

	public ResourceLocation getLocationSkin() {
//		if(locationSkin == null)
//			return AbstractClientPlayer.locationStevePng;
		return locationSkin;
	}

	public ResourceLocation getLocationCape() {
		return locationCape;
	}

	@Override
	public void onUpdate() {
		if(markedForDespawn){
			this.setDead();
			return;
		}
		super.onUpdate();
		if(world.isRemote && !init ){
			username = dataManager.get(USERNAME);
			uuid = UUID.fromString(dataManager.get(PLAYER_UUID));
			this.rotationYawHead = dataManager.get(ROTATION_YAW_HEAD);
			prevRotationYawHead = rotationYawHead;
			this.rotationYaw = rotationYawHead;	//I don't know how much of this that is necessary
			prevRotationYaw = rotationYaw;
			renderYawOffset = rotationYaw;
			isFlying = dataManager.get(FLYING);
			setupCustomSkin();
			init = true;
		}
		rotationYawHead = prevRotationYawHead;	//Neutralize the effect of the LookHelper
		rotationYaw = prevRotationYaw;
		rotationPitch = prevRotationPitch;

		if(isFlying)
			posY = prevPosY;

		if(!world.isRemote)
		{
			if(foodStats != null)
				foodStats.onUpdate(player);
			if(this.locationChanged())
				ServerEditHandler.reset(ServerEditHandler.getData(this));
		}
	}

	public boolean locationChanged() {
		return originX >= posX+1 || originX <= posX-1 ||
				originY >= posY+1 || originY <= posY-1 ||
				originZ >= posZ+1 || originZ <= posZ-1;
	}

	@Override
	public boolean attackEntityFrom(DamageSource damageSource, float par2) {
		if (!world.isRemote && (!gameType.equals(GameType.CREATIVE) || damageSource.canHarmInCreative()))
			ServerEditHandler.reset(damageSource, par2, ServerEditHandler.getData(this));
		return true;
	}

	@Override
	public boolean getAlwaysRenderNameTagForRender() {
		return username != null;
	}

	@Override
	public String getName()
	{
		return username != null ? username : "DECOY";
	}

	@Override
	public ItemStack getItemStackFromSlot(EntityEquipmentSlot slotIn)
	{
		if(slotIn == EntityEquipmentSlot.MAINHAND)
			return inventory.getCurrentItem();
		else if(slotIn == EntityEquipmentSlot.OFFHAND)
			return inventory.offHandInventory.get(0);
		else return inventory.armorInventory.get(slotIn.getIndex());
	}

	@Override
	public void setItemStackToSlot(EntityEquipmentSlot slotIn, ItemStack stack)
	{
		if(slotIn == EntityEquipmentSlot.MAINHAND)
			inventory.setInventorySlotContents(inventory.currentItem, stack);
		else if(slotIn == EntityEquipmentSlot.OFFHAND)
			inventory.offHandInventory.set(0, stack);
		else inventory.armorInventory.set(slotIn.getIndex(), stack);
	}

	@Override
	public void setHealth(float par1)
	{
		if(player != null)
			player.setHealth(par1);
		super.setHealth(par1);
	}

	@Override
	public Iterable<ItemStack> getArmorInventoryList()
	{
		return inventory.armorInventory;
	}

	@Override
	protected boolean canDespawn() {
		return false;
	}

	@SuppressWarnings("EntityConstructor")
	private static class DecoyPlayer extends FakePlayer	//Never spawned into the world. Only used for the InventoryPlayer and FoodStats.
	{

		EntityDecoy decoy;

		DecoyPlayer(WorldServer par1World, EntityDecoy decoy, EntityPlayerMP player)
		{
			super(par1World, player.getGameProfile());
			player.getServer().getPlayerList().getPlayerAdvancements(player);
			//Fixes annoying NullPointerException when unlocking advancement, caused by just creating the fake player
			this.decoy = decoy;
			this.setHealth(decoy.getHealth());
		}

		@Override
		public void heal(float par1)
		{
			decoy.heal(par1);
		}
	}
}