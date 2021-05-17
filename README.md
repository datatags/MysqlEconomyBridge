### MySQL Economy Bridge Bukkit/Spigot Minecraft Plugin
 
Fork of the original [MySQL](http://www.spigotmc.org/resources/mysql-economy-bridge.6174/) bridge plugin with
- Newer libs
- Legacy layer removed.
- Uses `com.mysql.cj.jdbc.Driver` instead of `com.mysql.jdbc.Driver` to remove the warning on Paper, does not work on Spigot at the time of writing!
- Rewrote a bunch of weird stuff (like `boolean == true`)
