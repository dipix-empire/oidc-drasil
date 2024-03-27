# Implementing OAuth
_In a compatible with oidc-drasil manner_

## General
You need common OAuth endpoints - authorization and token. This is the meat and juice of oauth after all.  
You also need introspection endpoint - it is used for verifying token validity.

## Minecraft scope
`minecraft` scope is used for authorizing profile management.
Client must request it to log into account's mc profiles.  
When server responds with access token, DO NOT include `minecraft` scope if the player is not allowed to play minecraft with this account.  
If the scope is requested and it is allowed to play, resulting token will contain minecraft claim like this:
```json
{
  "minecraft": {
    "max_profiles": 1
  }
}
```
Max profiles controls the amount of profiles that can be made. Note that profiles and users are different - profiles are like game saves. Each can be treated as separate minecraft player with its own skin and cape.