# LSPosed IPv6 DNS Last

### WARNING: This is **PURE** slop. (Sonnet 5.5)

This is an Xposed/Vector module to push the received IPv6 DNS from router announcements to minimum priority. Android by default adds it with the highest priority which prevents using Pi-hole. I am not interested in android development or Java. Don't expect this to receive support and fixes for other devices.

## Requirements

- Root access
- Xposed framwork that will pick this thing up

> When installing you need to change the target filter to allow seeing system apps.

## Tested setup

Sony Xperia 10 VII (XQ-FE72)  
Android 16 - Lineage OS 23.2 (23.2-20260517-NIGHTLY-pdx257)  
Zygisk Next 1.5.2 (864-a4a72ce-release)  
Vector v2.2 (3080-88f8e1fa-JingMatrix-Vector)  

## License

MIT License. Its slop do whatever the fuck you want with it idc.
