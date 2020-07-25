var TrezorConnect = require('cordova-plugin-trezor.TrezorConnect');

function registerManifest(success, error, params) {
    TrezorConnect.manifest(params[0]);
}

function getPublicKeys(success, error, params) {
    TrezorConnect.getPublicKey(params[0]).then(success).catch(error);
}

module.exports = {
    manifest: registerManifest,
    getPublicKeys: getPublicKeys
};

require('cordova/exec/proxy').add('CordovaTrezor', module.exports);