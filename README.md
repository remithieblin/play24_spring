# play24_spring

This demo Play application runs with Spring as the DI.

 The only thing to do to have it run is to add @javax.inject.Named next to the @Singleton annotations in the Play classes so that Spring can scan them and load them.

 Currently using Play 2.6.0-SNAPSHOT
