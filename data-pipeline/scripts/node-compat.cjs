if (process.platform === 'win32') {
  // Some restricted Windows hosts cannot resolve the current account through
  // os.userInfo(). tsx uses that value only to namespace its temp directory;
  // provide a stable local uid so the development launcher can still start.
  if (typeof process.geteuid !== 'function') process.geteuid = () => 0

  // `tsx watch` launches a child Node process. Propagate this preload through
  // NODE_OPTIONS so that child reaches the same safe temp-directory path.
  const nodeOptionsPath = __filename.replaceAll('\\', '/')
  const nodeOptions = process.env.NODE_OPTIONS ?? ''
  if (!nodeOptions.includes(nodeOptionsPath)) {
    const preload = `--require=${nodeOptionsPath}`
    process.env.NODE_OPTIONS = nodeOptions === '' ? preload : `${nodeOptions} ${preload}`
  }
}
