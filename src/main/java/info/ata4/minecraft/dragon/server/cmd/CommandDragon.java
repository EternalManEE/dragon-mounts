/*
 ** 2012 August 24
 **
 ** The author disclaims copyright to this source code.  In place of
 ** a legal notice, here is a blessing:
 **    May you do good and not evil.
 **    May you find forgiveness for yourself and forgive others.
 **    May you share freely, never taking more than you give.
 */
package info.ata4.minecraft.dragon.server.cmd;

import info.ata4.minecraft.dragon.server.entity.EntityTameableDragon;
import info.ata4.minecraft.dragon.server.entity.breeds.DragonBreed;
import info.ata4.minecraft.dragon.server.entity.helper.DragonBreedRegistry;
import info.ata4.minecraft.dragon.server.entity.helper.DragonLifeStage;
import net.minecraft.command.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 *
 * @author Nico Bergemann <barracuda415 at yahoo.de>
 */
public class CommandDragon extends CommandBase {

  @Override
  public String getName() {
      return "dragon";
  }

  @Override
  public String getCommandUsage(ICommandSender sender) {
      String stages = StringUtils.join(DragonLifeStage.values(), '|').toLowerCase();
      String breeds = StringUtils.join(DragonBreedRegistry.getInstance().getBreeds(), '|');
      return String.format("/dragon <stage <%s>|breed <%s> [global]", stages, breeds);
  }

  @Override
  public List addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos)
	{
		if (args.length == 1)
		{
			return getListOfStringsMatchingLastWord(args, "stage", "breed", "tame");
		}
		else
		{
			if (args[0].equalsIgnoreCase("stage"))
			{
				if (args.length == 2)
				{
					return getListOfStringsMatchingLastWord(args, "egg", "hatchling", "juvenile", "adult", "item");
				}
			}
			else if (args[0].equalsIgnoreCase("breed"))
			{
				if (args.length == 2)
				{
					return getListOfStringsMatchingLastWord(args, "water", "ice", "air", "ghost", "nether", "fire", "end");
				}
			}
		}
		return null;
	}
    
    /**
     * Return the required permission level for this command.
     */
    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public void execute(ICommandSender sender, String[] params) throws CommandException {
        if (params.length < 1 || params[0].isEmpty()) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        
        // last parameter, optional
        boolean global = params[params.length - 1].equalsIgnoreCase("global");

        String command = params[0];
        if (command.equals("stage")) {
            if (params.length < 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }

            DragonLifeStage lifeStage = null;
            String parameter = params[1].toUpperCase();
            
            if (!parameter.equals("ITEM")) {
                try {
                    lifeStage = DragonLifeStage.valueOf(parameter);
                } catch (IllegalArgumentException ex) {
                    throw new SyntaxErrorException();
                }
            }

            EntityModifier modifier = new LifeStageModifier(lifeStage);
            applyModifier(sender, modifier, global);
        } else if (command.equals("breed")) {
            if (params.length < 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            
            String breedName = params[1].toLowerCase();
            DragonBreed breed = DragonBreedRegistry.getInstance().getBreedByName(breedName);
            
            if (breed == null) {
                throw new SyntaxErrorException();
            }
            
            applyModifier(sender, new BreedModifier(breed), global);
        } else if (command.equals("tame")) {
            if (sender instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender;
                applyModifier(sender, new TameModifier(player), global);
            } else {
                // console can't tame dragons
                throw new CommandException("commands.dragon.canttame");
            }
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }
    
    private void applyModifier(ICommandSender sender, EntityModifier modifier, boolean global) throws CommandException {
        if (!global && sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            double range = 64;
            AxisAlignedBB aabb = new AxisAlignedBB(
                    player.posX - 1, player.posY - 1, player.posZ - 1,
                    player.posX + 1, player.posY + 1, player.posZ + 1);
            aabb = aabb.expand(range, range, range);
            List<Entity> entities = player.worldObj.getEntitiesWithinAABB(EntityTameableDragon.class, aabb);

            Entity closestEntity = null;
            float minPlayerDist = Float.MAX_VALUE;

            // get closest dragon
            for (int i = 0; i < entities.size(); i++) {
                Entity entity = entities.get(i);
                float playerDist = entity.getDistanceToEntity(player);
                if (entity.getDistanceToEntity(player) < minPlayerDist) {
                    closestEntity = entity;
                    minPlayerDist = playerDist;
                }
            }

            if (closestEntity == null) {
                throw new CommandException("commands.dragon.nodragons");
            } else {
                modifier.modify((EntityTameableDragon) closestEntity);
            }
        } else {
            // scan all entities on all dimensions
            MinecraftServer server = MinecraftServer.getServer();
            for (WorldServer worldServer : server.worldServers) {
                List<Entity> entities = worldServer.loadedEntityList;

                for (int i = 0; i < entities.size(); i++) {
                    Entity entity = entities.get(i);

                    if (!(entity instanceof EntityTameableDragon)) {
                        continue;
                    }

                    modifier.modify((EntityTameableDragon) entity);
                }
            }
        }
    }
    
    private interface EntityModifier {
        public void modify(EntityTameableDragon dragon);
    }
    
    private class LifeStageModifier implements EntityModifier {

        private DragonLifeStage lifeStage;
        
        LifeStageModifier(DragonLifeStage lifeStage) {
            this.lifeStage = lifeStage;
        }
        
        @Override
        public void modify(EntityTameableDragon dragon) {
            if (lifeStage == null) {
                dragon.getLifeStageHelper().transformToEgg();
            } else {
                dragon.getLifeStageHelper().setLifeStage(lifeStage);
            }
        }
    }
    
    private class BreedModifier implements EntityModifier {

        private DragonBreed breed;
        
        BreedModifier(DragonBreed breed) {
            this.breed = breed;
        }
        
        @Override
        public void modify(EntityTameableDragon dragon) {
            dragon.setBreed(breed);
        }
    }
    
    private class TameModifier implements EntityModifier {
        
        private EntityPlayerMP player;
        
        TameModifier(EntityPlayerMP player) {
            this.player = player;
        }

        @Override
        public void modify(EntityTameableDragon dragon) {
            dragon.tamedFor(player, true);
        }
    }
}
