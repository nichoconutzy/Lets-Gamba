# Lets-Gamba
Poker gambling plugin for servers running Paper on Java Minecraft 1.21.10

## Current Version: 0.6.6

### Summary
This Minecraft plugin adds in Texas Hold'em for Paper/Spigot Minecraft Java 1.21.10. It includes the main game in the chatbox, economy integration, poker table range detection, autojoin mechanics, and much more.

---
### Code Stack
Java

### Contributors
* choconutzy
* cyforce (RTHKKona)
* MWMothman

### Features
* Auto-join via walking up to the table/sitting at the chairs
* Accurate Texas Hold'em Poker for 2-6 player games
* Nitwit Employment with in-built tipping system
* Nitwit automatic pathing to a poker table (A 3x2 Green Wool Table with stairs on 3 sides.)
* Economy Integration (Vault)
* Custom Regional Nitwit clothing via SuitedNitwits v0.3
* Works with GSit at the poker table
* Pre-game and Post-game countdown
* buh


### Getting Started
Start by placing down 3x2 Green Wool. You want to also place slabs or stairs around three sides of the green wool. Leave one of the 3-long sides completely empty as this will be the side that the Nitwit dealer will stand.

Then find a nitwit and keep him at least within 40 blocks from the table itself. Only Nitwits are allowed to be dealers!

Initialize the poker table with /poker. You will need to be nearby to initialise it. The nitwit should've moved to the table and is awaiting players to join.
/poker join is available as well.

Play the game via the chatbox buttons. You can leave the game with /poker leave.

> For Operators Only - 
> You can use /poker override for testing purposes on your server if you need, as it enables singleplayer games. Note that it is coded so then you only play a single game before it raises redflags on the insufficient quantity of players. Remember to type it again to disable it as the command is for Global tables.


### Bugfixes/Changes/Improvements
* Add blind rotation with Dealer button
* Add visuals for flop and river on the wool table (Using [Blackjack](https://modrinth.com/plugin/bjplugin) for Paper as inspiration)
* Custom greenwool textures

---
* ~~Add in Economy support for Vault/EssentialsX plugin integration~~
* ~~Add Nitwit + Wool block table requirement for a valid poker table~~
* ~~Add Big blind and small blind betting along with mid-round custom bet/raise~~
* ~~Add a "You left the table." in red once /poker leave is typed~~
* ~~All-in button~~
* ~~10sec Pre-game countdown~~
* ~~If previous player raised/bet ==> next player must bet same amount/raise/fold~~
* ~~Poker game to start when /poker start is typed. Make sure to check for a minimum of 2 players and maximum of 6.~~
* ~~Optional show cards last 5 seconds after pot is paid (not tested)~~
* ~~Add Turn instead of Turn+River~~
* ~~Fix double you have left the poker table~~
