# About

Im not sure if this can be strictly considered a fork, but the project was originally inspired by [this datapack](https://modrinth.com/datapack/animated-doors). 

About a year ago, I wanted to install that datapack on my Paper server. Unfortunately, due to the poor optimization of datapacks in Minecraft, 
it didnt function correctly and consumed a massive amount of server resources. Sometime later, 
I recalled this issue and decided to write a custom plugin for animated doors that operates on the exact same principle as the original datapack, but with significantly better performance.

## Features

* **Smooth Door Animations:** Recreates the original visual aesthetic without the performance overhead.
* **Double Door Support:** Automatically opens two adjacent doors simultaneously for a more realistic feel.
* **Mass Conversion Tool:** Easily convert existing doors on your server to the new format using a single command.

## Installation

To dynamic animations work properly, you must add this content to your server-resourcepack:
* [Download Resource Pack](https://modrinth.com/resourcepack/animated-doors-alternative-rp)

## Commands & Configuration

If you are introducing this plugin to an existing world, you don't need to replace doors manually. Use the following command to automatically convert them:
```text
/animdoors <world> <center x> <center z> <radius>
