
# Introduction

[Sieve][1] is a simple mail filtering language. Users scripts are stored and run on
the mail server, and are managed through the [Manage Sieve][2] protocol. This library
provides a Java API to the Manage Sieve protocol.

# Dependencies

This library depends on [Apache Commons Codec][3] for Base64 support. It also depends
on [SLF4J][4] as its logging facade.

# Example

There is an example use of the libray in the 
com.fluffypeople.managesieve.examples package.

# Maven
    <dependency>
      <groupId>com.fluffypeople</groupId>
      <artifactId>managesievej</artifactId>
      <version>0.2.1</version>
    </dependency>

# Licence

This libary is covered by the MIT licence. 

# References

[1]: http://tools.ietf.org/html/rfc3028 "Sieve RFC"
[2]: http://tools.ietf.org/html/rfc5804 "Manage Sieve RFC"
[3]: http://commons.apache.org/proper/commons-codec/ "Apache Commons Codec"
[4]: http://www.slf4j.org/ "Simple Logging Facade for Java"
