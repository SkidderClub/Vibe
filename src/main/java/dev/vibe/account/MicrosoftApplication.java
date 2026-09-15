package dev.vibe.account;

/** Refresh tokens and Xbox RPS tickets must use the application that issued them. */
enum MicrosoftApplication {
    IAS("54fd49e4-2103-4044-9603-2b028c814ec3", "XboxLive.signin XboxLive.offline_access",
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token", "d="),
    MINECRAFT("00000000402b5328", "service::user.auth.xboxlive.com::MBI_SSL",
            "https://login.live.com/oauth20_token.srf", "t=");

    final String clientId, scope, tokenEndpoint, rpsPrefix;

    MicrosoftApplication(String clientId, String scope, String tokenEndpoint, String rpsPrefix) {
        this.clientId = clientId;
        this.scope = scope;
        this.tokenEndpoint = tokenEndpoint;
        this.rpsPrefix = rpsPrefix;
    }
}
