# Fantasy Sports Discord Bot

A Discord bot for fantasy sports

## Supported Platforms

* Yahoo!

## Before You Start

Before you start you will need to register an app as a developer for Discord
and Yahoo Fantasy.

* [Discord](https://www.upwork.com/resources/how-to-make-discord-bot)
  * You'll need to save the Token for your bot
* [Yahoo Fantasy](https://developer.yahoo.com/apps/create/)
  * You'll need to save the consumer key and secret

## Authorization

This part is quite advanced. For Yahoo, you need to acquire the access token and
refresh token by going through their [OAuth](https://developer.yahoo.com/oauth2/guide/)
flow. This is needed to access information from Yahoo.

## Configuration

The bot is configured with an [EDN](https://github.com/edn-format/edn) file,
whose path is passed to the the bot via the command line. Below is an example
configuration for Yahoo:

```clojure
{:discord {:token ""}
 :provider {:type :yahoo
 	    :sport :nba
	    :league-id 0
	    :auth {:client-id ""
	    	   :client-secret ""
	    	   :access-token ""
		   :refresh-token ""
		   :token-type "bearer"}}}
```

## Running the bot locally
```bash
lein run --config=<path to config>
```