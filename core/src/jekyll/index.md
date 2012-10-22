---
layout: index
title: Scalamachine
---

Write well-formed HTTP APIs in [Scala](http://www.scala-lang.org). Run them on 
[Netty](http://netty.io) or using your choice of javax.servlet container (support for more 
servers coming soon). 

Scalamachine is opinionated; its not for everyone. *It strives to treat HTTP as an application-level protocol, 
be lightweight, help you write referentially transparent code
and get out of your way when building the rest of your application*. 

You provide the details like what content-types your resources provide, 
cache details like etags and last modified timestamps and how requests are authorized. 
Scalamachine takes care of things like content-type negotiation, cache control and responding
on failed authorization. 



