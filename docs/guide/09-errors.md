# 9. Errors and exceptions

This chapter covers what a wallet app sees when something goes wrong, and the core's own log.

## The native log

`ZephyrUtils.configureNativeLogging(path, false)` sends the core's log to a file, for example in
`context.getFilesDir()`; on Android this has not been tried yet.
