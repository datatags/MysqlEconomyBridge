package net.craftersland.eco.bridge.events.handlers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import net.craftersland.eco.bridge.Eco;

public class EcoDataHandler {
	
	private Eco eco;
	private Map<Player, Double> backupMoney = new HashMap<Player, Double>();
	private Map<Player, Double> balanceMap = new HashMap<Player, Double>();
	private Map<Player, Integer> runningTasks = new HashMap<Player, Integer>();
	private Set<Player> playersInSync = new HashSet<Player>();
	
	public EcoDataHandler(Eco eco) {
		this.eco = eco;
	}
	
	public void onShutDownDataSave() {
		Eco.log.info("Saving online players data...");
		
		for (Player p : Bukkit.getOnlinePlayers()) {
			onDataSaveFunction(p, true, "true", true);
		}
		Eco.log.info("Data save complete for " + Bukkit.getOnlinePlayers().size() + " players.");
	}
	
	public void updateBalanceMap() {
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (playersInSync.contains(p)) {
				balanceMap.put(p, Eco.vault.getBalance(p));
			}
		}
	}
	
	public boolean isSyncComplete(Player p) {
		return playersInSync.contains(p);
	}
	
	private void dataCleanup(Player p) {
		playersInSync.remove(p);
		backupMoney.remove(p);
		balanceMap.remove(p);
		if (runningTasks.containsKey(p)) {
			Bukkit.getScheduler().cancelTask(runningTasks.get(p));
			runningTasks.remove(p);
		}
	}
	
	private void setPlayerData(final Player p, String[] data, boolean cancelTask) {
		try {
			setPlayerMoney(p, data[0]);
		} catch (Exception e) {
			e.printStackTrace();
			Double backupBalance = backupMoney.get(p);
			if (backupBalance != 0.0) {
				setPlayerMoney(p, data[0]);
			}
		}
		eco.getEcoMysqlHandler().setSyncStatus(p, "false");
		playersInSync.add(p);
		backupMoney.remove(p);
		if (cancelTask) {
			int taskID = runningTasks.remove(p);
			Bukkit.getScheduler().cancelTask(taskID);
		}
	}
	
	private void setPlayerMoney(Player p, String stringBal) {
		double bal = Eco.vault.getBalance(p);
		if (bal != 0) {
			Eco.vault.withdrawPlayer(p, bal);
		}
		double localBal = Eco.vault.getBalance(p);
		double newBal = Double.parseDouble(stringBal);
		if (newBal >= localBal) {
			double finalBalance = newBal - localBal;
			Eco.vault.depositPlayer(p, finalBalance);
		} else {
			double finalBalance = localBal - newBal;
			Eco.vault.withdrawPlayer(p, finalBalance);
		}
	}
	
	public void onDataSaveFunction(Player p, boolean datacleanup, String syncStatus, boolean isDisabling) {
		boolean isPlayerInSync = playersInSync.contains(p);
		Double newBalance = null;
		if (isDisabling) {
			newBalance = balanceMap.get(p);
		} else {
			if (datacleanup) {
				dataCleanup(p);
			}
			newBalance = Eco.vault.getBalance(p);
		}
		if (!isPlayerInSync || newBalance == null) return;
		eco.getEcoMysqlHandler().setData(p, newBalance, syncStatus);
	}
	
	public void onJoinFunction(final Player p) {
		if (!eco.getEcoMysqlHandler().hasAccount(p)) {
			playersInSync.add(p);
			onDataSaveFunction(p, false, "false", false);
			return;
		}
		final double balance = Eco.vault.getBalance(p);
		backupMoney.put(p, balance);
		Bukkit.getScheduler().runTask(eco, new Runnable() {
			@Override
			public void run() {
				if (balance != 0.0) {
					Eco.vault.withdrawPlayer(p, balance);
				}
				String[] data = eco.getEcoMysqlHandler().getData(p);
				if (data[1].contains("true")) {
					setPlayerData(p, data, false);
					return;
				}
				final long taskStart = System.currentTimeMillis();
				BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(eco, new Runnable() {

					@Override
					public void run() {
						if (System.currentTimeMillis() - taskStart >= 10 * 1000) {
							int taskID = runningTasks.get(p);
							runningTasks.remove(p);
							Bukkit.getScheduler().cancelTask(taskID);
						}
						if (!p.isOnline()) return;
						final String[] data = eco.getEcoMysqlHandler().getData(p);
						Bukkit.getScheduler().runTask(eco, new Runnable() {
							@Override
							public void run() {
								if (data[1].contains("true") // equals() instead?
										|| System.currentTimeMillis() - Long.parseLong(data[2]) >= 15 * 1000) {
									setPlayerData(p, data, true);
								}
							}
							
						});
					}
					
				}, 20L, 20L);
				runningTasks.put(p, task.getTaskId());
			}
			
		});
	}

}
