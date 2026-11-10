/**
 * Copyright (c) woodser
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <jni.h>

#ifndef _Included_MoneroJniBridge // TODO: rename
#define _Included_MoneroJniBridge

// TODO: this causes warning
std::string jstring2string(JNIEnv *env, jstring jStr);

jfieldID get_handle_field(JNIEnv *env, jobject obj, const char *field_name) {
  jclass c = env->GetObjectClass(obj);
  return env->GetFieldID(c, field_name, "J"); // of type long
}

template<typename T>
T *get_handle(JNIEnv *env, jobject obj, const char *field_name) {
  jlong handle = env->GetLongField(obj, get_handle_field(env, obj, field_name));
  return reinterpret_cast<T *>(handle);
}

#ifdef __cplusplus
extern "C" {
#endif

// ------------------------------ STATIC UTILS --------------------------------

JNIEXPORT jstring JNICALL Java_zephyr_common_ZephyrUtils_getIntegratedAddressJni(JNIEnv *, jclass, jint, jstring, jstring);

JNIEXPORT void JNICALL Java_zephyr_common_ZephyrUtils_validateAddressJni(JNIEnv *, jclass, jstring, jint);

JNIEXPORT jbyteArray JNICALL Java_zephyr_common_ZephyrUtils_jsonToBinaryJni(JNIEnv *, jclass, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_common_ZephyrUtils_binaryToJsonJni(JNIEnv *, jclass, jbyteArray);

JNIEXPORT jstring JNICALL Java_zephyr_common_ZephyrUtils_binaryBlocksToJsonJni(JNIEnv *, jclass, jbyteArray);

JNIEXPORT void JNICALL Java_zephyr_common_ZephyrUtils_setLogLevelJni(JNIEnv *, jclass, jint);

JNIEXPORT void JNICALL Java_zephyr_common_ZephyrUtils_configureLoggingJni(JNIEnv *, jclass, jstring jpath, jboolean);

// ------------------------------- TLS (ZephyrTls) --------------------------------

JNIEXPORT void JNICALL Java_zephyr_common_ZephyrTls_setEnvJni(JNIEnv *, jclass, jstring, jstring);

// --------------------------- STATIC WALLET UTILS ----------------------------

JNIEXPORT jboolean JNICALL Java_zephyr_wallet_ZephyrWalletFull_walletExistsJni(JNIEnv *, jclass, jstring);

JNIEXPORT jlong JNICALL Java_zephyr_wallet_ZephyrWalletFull_openWalletJni(JNIEnv *, jclass, jstring, jstring, jint);

JNIEXPORT jlong JNICALL Java_zephyr_wallet_ZephyrWalletFull_openWalletDataJni(JNIEnv *, jclass, jstring, jint, jbyteArray, jbyteArray);

JNIEXPORT jlong JNICALL Java_zephyr_wallet_ZephyrWalletFull_createWalletJni(JNIEnv *, jclass, jstring);

JNIEXPORT jobjectArray JNICALL Java_zephyr_wallet_ZephyrWalletFull_getMnemonicLanguagesJni(JNIEnv *, jclass);

// ----------------------------- INSTANCE METHODS -----------------------------

JNIEXPORT jboolean JNICALL Java_zephyr_wallet_ZephyrWalletFull_isViewOnlyJni(JNIEnv *, jobject);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_setDaemonConnectionJni(JNIEnv *, jobject, jstring, jstring, jstring);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_setProxyJni(JNIEnv *, jobject, jstring);

JNIEXPORT jobjectArray JNICALL Java_zephyr_wallet_ZephyrWalletFull_getDaemonConnectionJni(JNIEnv *, jobject);

JNIEXPORT jboolean JNICALL Java_zephyr_wallet_ZephyrWalletFull_isConnectedToDaemonJni(JNIEnv *, jobject);

JNIEXPORT jboolean JNICALL Java_zephyr_wallet_ZephyrWalletFull_isDaemonSyncedJni(JNIEnv *, jobject);

JNIEXPORT jboolean JNICALL Java_zephyr_wallet_ZephyrWalletFull_isSyncedJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getVersionJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getPathJni(JNIEnv *, jobject);

JNIEXPORT jint JNICALL Java_zephyr_wallet_ZephyrWalletFull_getNetworkTypeJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getMnemonicJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getMnemonicLanguageJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getPublicViewKeyJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getPrivateViewKeyJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getPublicSpendKeyJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getPrivateSpendKeyJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getAddressJni(JNIEnv *, jobject, jint, jint);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getAddressIndexJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getIntegratedAddressJni(JNIEnv *, jobject, jstring, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_decodeIntegratedAddressJni(JNIEnv *, jobject, jstring);

JNIEXPORT jlong JNICALL Java_zephyr_wallet_ZephyrWalletFull_getHeightJni(JNIEnv *, jobject);

JNIEXPORT jlong JNICALL Java_zephyr_wallet_ZephyrWalletFull_getRestoreHeightJni(JNIEnv *, jobject);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_setRestoreHeightJni(JNIEnv *, jobject, jlong);

JNIEXPORT jlong JNICALL Java_zephyr_wallet_ZephyrWalletFull_getDaemonHeightJni(JNIEnv *, jobject);

JNIEXPORT jlong JNICALL Java_zephyr_wallet_ZephyrWalletFull_getDaemonMaxPeerHeightJni(JNIEnv *, jobject);

JNIEXPORT jlong JNICALL Java_zephyr_wallet_ZephyrWalletFull_getHeightByDateJni(JNIEnv *, jobject, jint, jint, jint);

JNIEXPORT jlong JNICALL Java_zephyr_wallet_ZephyrWalletFull_setListenerJni(JNIEnv *, jobject, jobject);

JNIEXPORT jobjectArray JNICALL Java_zephyr_wallet_ZephyrWalletFull_syncJni(JNIEnv *, jobject, jlong);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_startSyncing(JNIEnv *, jobject, jlong);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_stopSyncing(JNIEnv *, jobject);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_scanTxsJni(JNIEnv *, jobject, jobjectArray);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_rescanSpentJni(JNIEnv *, jobject);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_rescanBlockchainJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getBalanceWalletJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getBalanceAccountJni(JNIEnv *, jobject, jint);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getBalanceAssetAccountJni(JNIEnv *, jobject, jstring, jint);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getBalanceAssetSubaddressJni(JNIEnv *, jobject, jstring, jint, jint);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getUnlockedBalanceWalletJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getUnlockedBalanceAccountJni(JNIEnv *, jobject, jint);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getUnlockedBalanceAssetAccountJni(JNIEnv *, jobject, jstring, jint);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getUnlockedBalanceAssetSubaddressJni(JNIEnv *, jobject, jstring, jint, jint);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getReserveInfoJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getCirculatingSupplyJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getAccountsJni(JNIEnv *, jobject, jboolean, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getAccountJni(JNIEnv *, jobject, jint, jboolean);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_createAccountJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getSubaddressesJni(JNIEnv *, jobject, jint, jintArray);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_createSubaddressJni(JNIEnv *, jobject, jint, jstring);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_setSubaddressLabelJni(JNIEnv *, jobject, jint, jint, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getTxsJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getTransfersJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getOutputsJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_exportKeyImagesJni(JNIEnv *, jobject, jboolean);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_importKeyImagesJni(JNIEnv *, jobject, jstring);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_freezeOutputJni(JNIEnv *, jobject, jstring);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_thawOutputJni(JNIEnv *, jobject, jstring);

JNIEXPORT bool JNICALL Java_zephyr_wallet_ZephyrWalletFull_isOutputFrozenJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_createTxsJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_sweepUnlockedJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_sweepOutputJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_sweepDustJni(JNIEnv *, jobject, jboolean);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_describeTxSetJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_signTxsJni(JNIEnv *, jobject, jstring);

JNIEXPORT jobjectArray JNICALL Java_zephyr_wallet_ZephyrWalletFull_submitTxsJni(JNIEnv *, jobject, jstring);

JNIEXPORT jobjectArray JNICALL Java_zephyr_wallet_ZephyrWalletFull_relayTxsJni(JNIEnv *, jobject, jobjectArray);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_signMessageJni(JNIEnv *, jobject, jstring, jint, jint, jint);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_verifyMessageJni(JNIEnv *, jobject, jstring, jstring, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getTxKeyJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_checkTxKeyJni(JNIEnv *, jobject, jstring, jstring, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getTxProofJni(JNIEnv *, jobject, jstring, jstring, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_checkTxProofJni(JNIEnv *, jobject, jstring, jstring, jstring, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getSpendProofJni(JNIEnv *, jobject, jstring, jstring);

JNIEXPORT jboolean JNICALL Java_zephyr_wallet_ZephyrWalletFull_checkSpendProofJni(JNIEnv *, jobject, jstring, jstring, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getReserveProofWalletJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getReserveProofAccountJni(JNIEnv *, jobject, jint, jstring, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_checkReserveProofJni(JNIEnv *, jobject, jstring, jstring, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getPaymentUriJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_parsePaymentUriJni(JNIEnv *, jobject, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_exportOutputsJni(JNIEnv *, jobject, jboolean);

JNIEXPORT jint JNICALL Java_zephyr_wallet_ZephyrWalletFull_importOutputsJni(JNIEnv *, jobject, jstring);

JNIEXPORT jobjectArray JNICALL Java_zephyr_wallet_ZephyrWalletFull_getTxNotesJni(JNIEnv *, jobject, jobjectArray);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_setTxNotesJni(JNIEnv *, jobject, jobjectArray, jobjectArray);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getAddressBookEntriesJni(JNIEnv *, jobject, jintArray);

JNIEXPORT jint JNICALL Java_zephyr_wallet_ZephyrWalletFull_addAddressBookEntryJni(JNIEnv *, jobject, jstring, jstring);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_editAddressBookEntryJni(JNIEnv *, jobject, jint, jboolean, jstring, jboolean, jstring);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_deleteAddressBookEntryJni(JNIEnv *, jobject, jint);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getAttributeJni(JNIEnv *, jobject, jstring);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_setAttributeJni(JNIEnv *, jobject, jstring, jstring);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_startMiningJni(JNIEnv *, jobject, jlong, jboolean, jboolean);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_stopMiningJni(JNIEnv *, jobject);

JNIEXPORT jboolean JNICALL Java_zephyr_wallet_ZephyrWalletFull_isMultisigImportNeededJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_getMultisigInfoJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_prepareMultisigJni(JNIEnv *, jobject);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_makeMultisigJni(JNIEnv *, jobject, jobjectArray, jint, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_exchangeMultisigKeysJni(JNIEnv *, jobject, jobjectArray, jstring);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_exportMultisigHexJni(JNIEnv *, jobject);

JNIEXPORT jint JNICALL Java_zephyr_wallet_ZephyrWalletFull_importMultisigHexJni(JNIEnv *, jobject, jobjectArray);

JNIEXPORT jstring JNICALL Java_zephyr_wallet_ZephyrWalletFull_signMultisigTxHexJni(JNIEnv *, jobject, jstring);

JNIEXPORT jobjectArray JNICALL Java_zephyr_wallet_ZephyrWalletFull_submitMultisigTxHexJni(JNIEnv *, jobject, jstring);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_changePasswordJniJni(JNIEnv *, jobject, jstring, jstring);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_moveToJni(JNIEnv *, jobject, jstring, jstring);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_saveJni(JNIEnv *, jobject);

JNIEXPORT void JNICALL Java_zephyr_wallet_ZephyrWalletFull_closeJni(JNIEnv *, jobject, jboolean);

JNIEXPORT jbyteArray JNICALL Java_zephyr_wallet_ZephyrWalletFull_getKeysFileBufferJni(JNIEnv *, jobject, jstring, jboolean);

JNIEXPORT jbyteArray JNICALL Java_zephyr_wallet_ZephyrWalletFull_getCacheFileBufferJni(JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif
