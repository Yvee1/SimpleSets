# SimpleSets
This repository contains a prototype implementation of SimpleSets.
Given a set of points in the plane that each belong to one category, SimpleSets creates simple shapes that enclose patterns in the data.

## Remarks
This implementation does not deal with the case where a stacking preference cannot be satisfied. 
However, this does not occur in any dataset we have encountered.
Euler spirals for smooth curves are approximated using Hobby's algorithm.
The implementation is sensitive to the distribution of points.
Areas that are too dense (either by using a large point size or having close point positions) may cause the implementation to crash.
In particular, if in the web page the loading bar of Computing... is stuck at the end, then the implementation has crashed behind the scenes.
In such a case, please reload the web page and try again with a smaller point size setting.
