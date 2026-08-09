hello i am Xploit providing this mod
this mod is made for the players that want to make a iceboat server but they cant really do that because of the prices most of the iceboat race plugins are paid that's why 
so i am here to make this good here are all the command and their description pls enjoy it.
Command	                Description
/boatrace create <name>   	Creates a new, empty race
/boatrace delete <name>	    Deletes a race entirely
/boatrace                   list	Lists every race and its current status/queue
/boatrace info <name>	      Full details on one race — status, players, timings, locations set, etc.
/boatrace clone <old> <new> Copies all settings from one race into a new one
/boatrace rename <old> <new>	Renames a race
/boatrace reload	           Reloads all races from config.yml

Locations

Command	                           Description
/boatrace <name> setlobby	Sets where players go after finishing/leaving/being stopped
/boatrace <name> setwaiting	Sets where players wait while queued (also setspawn)
/boatrace <name> setfinish	Sets the finish line
/boatrace <name> setspectator	Sets where spectators are teleported to

Settings

Command                                	Description
/boatrace <name> setminplayers <n>	Minimum players needed to run
/boatrace <name> setmaxplayers <n>	Max players / queue fills at this number
/boatrace <name> setcountdown <seconds>	Pre-race countdown length
/boatrace <name> setlaps <number>	How many laps to finish
/boatrace <name> setqueuetimeout <seconds>	Force-starts this long after the first player joins, if queue isn't full
/boatrace <name> setboat <oak|spruce|...>	Which boat type racers get
/boatrace <name> setweather <clear|rain|thunder|none>	Weather applied to the lane world on start
/boatrace <name> settime <day|noon|night|midnight|none|ticks>	Time of day applied on start
/boatrace <name> setdescription <text>	Description shown in /boatrace info
/boatrace <name> setdifficulty <text>	Difficulty label
/boatrace <name> setauthor <text>	Credit/author label

Lanes & checkpoints

Command	                                Description
/boatrace <name> setlane <lane>	Sets a lane's start point to where you're standing
/boatrace <name> removelane <lane>	Removes a lane
/boatrace <name> listlanes	Lists all configured lane numbers
/boatrace <name> checklanes	Shows whether anything is currently sitting on each lane spot
/boatrace <name> checkpoint add	Adds a checkpoint where you're standing
/boatrace <name> checkpoint remove <id>	Removes a checkpoint
/boatrace <name> checkpoint list	Shows the checkpoint count

Control

Command	                            Description
/boatrace <name> start / forcestart	Starts the race now regardless of queue size
/boatrace <name> stop / forcestop	Stops a running race immediately
/boatrace <name> reset	Clears the queue without starting anything
/boatrace <name> enable	Turns the race on (must pass validate first)
/boatrace <name> disable	Turns the race off
/boatrace <name> validate	Full readiness check across every system

Holograms

Command	Description
/boatrace hologram create <race> <wins|fastest|queue>	Places a live floating-text display
/boatrace hologram remove	Removes the nearest one
Player commands (/race...)
Command	Description
/race	Opens the main menu
/race join <name>	Joins a race's queue
/race leave <name>	Leaves a specific race's queue
/race leavequeue	Leaves whichever queue you're in (no name needed)
/race list	Lists all races and their live queue counts
/race spectate <name>	Starts spectating a race
/race leavespectate	Stops spectating
/race stats	Your personal wins/losses/streak stats
/race leaderboard <name> <wins|fastest>	Shows a leaderboard
/race help	Shows this command list in-game
