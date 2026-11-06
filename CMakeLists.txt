cmake_minimum_required(VERSION 3.4.1)

# C++ standard and flags as in zephyr-cpp's CMakeLists.txt (c++14)
if (WIN32)
  add_definitions( "-D_GLIBCXX_USE_NANOSLEEP=1" )
  add_definitions( "-DWIN32_LEAN_AND_MEAN" )
  SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wa,-mbig-obj -O2 -fPIC -std=c++14 -F/Library/Frameworks -pthread -lcrypto -lcrypt32")
else()
  SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC -std=c++14 -F/Library/Frameworks -pthread")
endif()

#SET(CMAKE_C_COMPILER /path/to/c/compiler)
#SET(CMAKE_CXX_COMPILER /path/to/cpp/compiler)

project(zephyr-java-jni)

#############
# Paths
#############

set(ZEPHYR_CPP "${CMAKE_SOURCE_DIR}/external/zephyr-cpp" CACHE STRING "zephyr-cpp source directory")
set(MONERO_PROJECT "${ZEPHYR_CPP}/external/zephyr" CACHE STRING "zephyr source directory")
set(MONERO_PROJECT_BUILD "${MONERO_PROJECT}/build/release" CACHE STRING "zephyr build directory")
set(ZEPHYR_CPP_SRC "${ZEPHYR_CPP}/src")
set(MONERO_PROJECT_SRC "${MONERO_PROJECT}/src")
message(STATUS "Using zephyr-cpp at ${ZEPHYR_CPP}")
message(STATUS "Using zephyr build at ${MONERO_PROJECT_BUILD}")

# find modules: zephyr-cpp's cmake/ (HIDAPI) and zephyr's cmake/ (Unbound), as in zephyr-cpp
list(INSERT CMAKE_MODULE_PATH 0 "${ZEPHYR_CPP}/cmake")
list(APPEND CMAKE_MODULE_PATH "${MONERO_PROJECT}/cmake")

# java header includes
include_directories("$ENV{JAVA_HOME}")
include_directories("$ENV{JAVA_HOME}/include")
if (APPLE)
  include_directories("$ENV{JAVA_HOME}/include/darwin")
elseif (WIN32)
  include_directories("$ENV{JAVA_HOME}/include/win32")
else()
  include_directories("$ENV{JAVA_HOME}/include/linux")
endif()

# zephyr-cpp and zephyr header includes: zephyr-cpp's list plus its src/, and the
# crypto/ directory the bridge needs for "chacha.h"
include_directories("${ZEPHYR_CPP_SRC}/")
include_directories("${ZEPHYR_CPP}/external/libsodium/include/sodium")
include_directories("${MONERO_PROJECT}/contrib/epee/include")
include_directories("${MONERO_PROJECT}/external/")
include_directories("${MONERO_PROJECT}/external/easylogging++")
include_directories("${MONERO_PROJECT}/external/rapidjson/include")
include_directories("${MONERO_PROJECT_SRC}/")
include_directories("${MONERO_PROJECT_SRC}/crypto")
include_directories("${MONERO_PROJECT_SRC}/crypto/crypto_ops_builder/include/")
include_directories("${MONERO_PROJECT_SRC}/wallet")
include_directories("${MONERO_PROJECT_SRC}/wallet/api")
include_directories("${MONERO_PROJECT_SRC}/hardforks")

####################
# Extra libraries
####################

if (APPLE)
  if (DEPENDS)
    list(APPEND EXTRA_LIBRARIES "-framework Foundation -framework IOKit -framework AppKit")
  else()
    find_library(COREFOUNDATION CoreFoundation)
    find_library(IOKIT IOKit)
    find_library(APPKIT AppKit)
    list(APPEND EXTRA_LIBRARIES ${IOKIT})
    list(APPEND EXTRA_LIBRARIES ${COREFOUNDATION})
    list(APPEND EXTRA_LIBRARIES ${APPKIT})
  endif()
endif()
if (WIN32)
  list(APPEND EXTRA_LIBRARIES setupapi)
endif()

message(STATUS EXTRA_LIBRARIES: ${EXTRA_LIBRARIES})

############
# Protobuf
############

if (NOT APPLE)
  include(FindProtobuf)
  find_package(Protobuf)
  message(STATUS "Protobuf lib: ${Protobuf_LIBRARY}, inc: ${Protobuf_INCLUDE_DIR}, protoc: ${Protobuf_PROTOC_EXECUTABLE}")
  include_directories(${Protobuf_INCLUDE_DIR})
endif()

############
# LibUSB
############

find_library(usb_LIBRARY NAMES usb-1.0 libusb usb)
set(LibUSB_LIBRARIES ${usb_LIBRARY})

############
# Boost
############

set(Boost_NO_BOOST_CMAKE 1)
set(Boost_USE_MULTITHREADED ON)
find_package(Boost 1.58 QUIET REQUIRED COMPONENTS chrono date_time filesystem program_options regex serialization wserialization system thread)
message(STATUS "Using Boost include dir at ${Boost_INCLUDE_DIR}")
include_directories(${Boost_INCLUDE_DIR})

############
# OpenSSL
############

if (APPLE AND NOT IOS)
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=x86-64 -fvisibility=default -std=c++11")
  if (NOT OPENSSL_ROOT_DIR)
      EXECUTE_PROCESS(COMMAND brew --prefix openssl
        OUTPUT_VARIABLE OPENSSL_ROOT_DIR
        OUTPUT_STRIP_TRAILING_WHITESPACE)
    message(STATUS "Using OpenSSL found at ${OPENSSL_ROOT_DIR}")
  endif()
endif()

find_package(OpenSSL REQUIRED)
message(STATUS "Using OpenSSL include dir at ${OPENSSL_INCLUDE_DIR}")
include_directories(${OPENSSL_INCLUDE_DIR})

if(STATIC AND NOT IOS)
  if(UNIX)
    set(OPENSSL_LIBRARIES "${OPENSSL_LIBRARIES};${CMAKE_DL_LIBS};${CMAKE_THREAD_LIBS_INIT}")
  endif()
endif()

if (WIN32)
  list(APPEND OPENSSL_LIBRARIES ws2_32 crypt32)
endif()

############
# libsodium
############

find_library(SODIUM_LIBRARY sodium REQUIRED)
message(STATUS "Using libsodium library at ${SODIUM_LIBRARY}")

############
# HIDAPI
############

if(APPLE)
  include_directories(SYSTEM /usr/include/malloc)
  if(POLICY CMP0042)
    cmake_policy(SET CMP0042 NEW)
  endif()
endif()

find_package(HIDAPI REQUIRED)
message(STATUS "Using HIDAPI include dir at ${HIDAPI_INCLUDE_DIR}")
add_definitions(-DHAVE_HIDAPI)
include_directories(${HIDAPI_INCLUDE_DIR})

#############
# zephyr static libraries (same list and paths as zephyr-cpp's CMakeLists.txt)
#############

add_library(wallet STATIC IMPORTED)
set_target_properties(wallet PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/lib/libwallet.a)

# libwallet-crypto.a provides x86_64 asm for some wallet functions
if (EXISTS ${MONERO_PROJECT_BUILD}/src/crypto/wallet/libwallet-crypto.a)
  add_library(wallet_crypto_lib STATIC IMPORTED)
  set_target_properties(wallet_crypto_lib PROPERTIES IMPORTED_LOCATION
      ${MONERO_PROJECT_BUILD}/src/crypto/wallet/libwallet-crypto.a)
  set(wallet_crypto wallet_crypto_lib)
endif()

add_library(lmdb STATIC IMPORTED)
set_target_properties(lmdb PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/external/db_drivers/liblmdb/liblmdb.a)

add_library(epee STATIC IMPORTED)
set_target_properties(epee PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/contrib/epee/src/libepee.a)

# Unbound
find_package(Unbound)
if(NOT UNBOUND_INCLUDE_DIR)
  die("Could not find libunbound")
else()
  message(STATUS "Found libunbound include (unbound.h) in ${UNBOUND_INCLUDE_DIR}")
  if(UNBOUND_LIBRARIES)
    message(STATUS "Found libunbound library")
    include_directories(${UNBOUND_INCLUDE_DIR})
    if (WIN32)
      add_library(unbound STATIC IMPORTED)
    else()
      add_library(unbound SHARED IMPORTED)
    endif()
    set_target_properties(unbound PROPERTIES IMPORTED_LOCATION ${UNBOUND_LIBRARIES})
  else()
    die("Found libunbound includes, but could not find libunbound library. Please make sure you have installed libunbound or libunbound-dev or the equivalent")
  endif()
endif()

add_library(rpc_base STATIC IMPORTED)
set_target_properties(rpc_base PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/rpc/librpc_base.a)

add_library(net STATIC IMPORTED)
set_target_properties(net PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/net/libnet.a)

add_library(hardforks STATIC IMPORTED)
set_target_properties(hardforks PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/hardforks/libhardforks.a)

add_library(easylogging STATIC IMPORTED)
set_target_properties(easylogging PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/external/easylogging++/libeasylogging.a)

add_library(cryptonote_core STATIC IMPORTED)
set_target_properties(cryptonote_core PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/cryptonote_core/libcryptonote_core.a)

add_library(cryptonote_protocol STATIC IMPORTED)
set_target_properties(cryptonote_protocol PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/cryptonote_protocol/libcryptonote_protocol.a)

add_library(cryptonote_basic STATIC IMPORTED)
set_target_properties(cryptonote_basic PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/cryptonote_basic/libcryptonote_basic.a)

add_library(cryptonote_format_utils_basic STATIC IMPORTED)
set_target_properties(cryptonote_format_utils_basic PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/cryptonote_basic/libcryptonote_format_utils_basic.a)

add_library(mnemonics STATIC IMPORTED)
set_target_properties(mnemonics PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/mnemonics/libmnemonics.a)

add_library(common STATIC IMPORTED)
set_target_properties(common PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/common/libcommon.a)

add_library(cncrypto STATIC IMPORTED)
set_target_properties(cncrypto PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/crypto/libcncrypto.a)

add_library(ringct STATIC IMPORTED)
set_target_properties(ringct PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/ringct/libringct.a)

add_library(ringct_basic STATIC IMPORTED)
set_target_properties(ringct_basic PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/ringct/libringct_basic.a)

add_library(blockchain_db STATIC IMPORTED)
set_target_properties(blockchain_db PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/blockchain_db/libblockchain_db.a)

# D22: no blocks library; zephyr builds with PER_BLOCK_CHECKPOINT 0, so libblocks.a never exists

add_library(checkpoints STATIC IMPORTED)
set_target_properties(checkpoints PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/checkpoints/libcheckpoints.a)

add_library(device STATIC IMPORTED)
set_target_properties(device PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/device/libdevice.a)

add_library(device_trezor STATIC IMPORTED)
set_target_properties(device_trezor PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/device_trezor/libdevice_trezor.a)

add_library(multisig STATIC IMPORTED)
set_target_properties(multisig PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/multisig/libmultisig.a)

add_library(version STATIC IMPORTED)
set_target_properties(version PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/src/libversion.a)

add_library(randomx STATIC IMPORTED)
set_target_properties(randomx PROPERTIES IMPORTED_LOCATION
    ${MONERO_PROJECT_BUILD}/external/randomx/librandomx.a)

###############################################
# Build the JNI library: bridge plus zephyr-cpp
###############################################

# zephyr-cpp's LIBRARY_SRC_FILES, compiled into this library; no separate libmonero-cpp (D17)
set(
    ZEPHYR_CPP_SRC_FILES
    ${ZEPHYR_CPP}/src/utils/gen_utils.cpp
    ${ZEPHYR_CPP}/src/utils/monero_utils.cpp
    ${ZEPHYR_CPP}/src/daemon/monero_daemon_model.cpp
    ${ZEPHYR_CPP}/src/daemon/monero_daemon.cpp
    ${ZEPHYR_CPP}/src/wallet/monero_wallet_model.cpp
    ${ZEPHYR_CPP}/src/wallet/monero_wallet_keys.cpp
    ${ZEPHYR_CPP}/src/wallet/monero_wallet_full.cpp
)

set(
    JNI_SRC_FILES
    src/main/cpp/zephyr_jni_bridge.cpp
)

add_library(zephyr-java SHARED ${JNI_SRC_FILES} ${ZEPHYR_CPP_SRC_FILES})

# D20: zephyr-cpp's monero_wallet_model.h uses std::map without including <map>;
# boost 1.77 and later no longer include it transitively
target_compile_options(zephyr-java PRIVATE -include map)

target_link_libraries(zephyr-java
    ssl
    crypto

    wallet
    rpc_base
    net
    lmdb
    unbound
    easylogging
    cryptonote_core
    cryptonote_protocol
    cryptonote_basic
    cryptonote_format_utils_basic
    mnemonics
    ringct
    ringct_basic
    common
    cncrypto
    blockchain_db
    checkpoints
    device
    device_trezor
    multisig
    version
    randomx
    epee
    hardforks

    ${wallet_crypto}

    ${UNBOUND_LIBRARIES}
    ${Boost_LIBRARIES}
    ${Protobuf_LIBRARY}
    ${LibUSB_LIBRARIES}
    ${OPENSSL_LIBRARIES}
    ${SODIUM_LIBRARY}
    ${HIDAPI_LIBRARIES}
    ${EXTRA_LIBRARIES}
)

# D17: symbols of the statically linked libraries stay local, so only the bridge's
# JNI entry points are exported from libzephyr-java
target_link_options(zephyr-java PRIVATE "-Wl,--exclude-libs,ALL")

if (WIN32)
  target_link_options(zephyr-java PUBLIC "-Wl,--enable-auto-import,--export-all-symbols")
endif()

INSTALL(TARGETS zephyr-java
  RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR} COMPONENT Runtime
  LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR} COMPONENT Runtime
  ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR} COMPONENT Development)
