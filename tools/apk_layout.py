"""Resource-table packaging contract, checked on the final signed archive."""
import io, struct, zipfile

RESOURCE_ALIGNMENT=4096

def write_entry(archive,name,data):
    # Android R+ requires STORED resources. zipalign cannot fix DEFLATED data.
    compressed_pack=name.startswith('assets/xiaomi/packs/') and name.endswith('.xz')
    direct_media=name.startswith('assets/flyme-video/') and name.endswith('.mp4')
    entry=zipfile.ZipInfo(name,date_time=(2009,1,1,0,0,0))
    entry.create_system=3
    entry.external_attr=0o100644 << 16
    archive.writestr(entry,data,compress_type=zipfile.ZIP_STORED if name=='resources.arsc' or compressed_pack or direct_media else zipfile.ZIP_DEFLATED,compresslevel=9)

def inspect(data,alignment=RESOURCE_ALIGNMENT):
    source=io.BytesIO(data)
    with zipfile.ZipFile(source) as archive:
        item=archive.getinfo('resources.arsc')
        source.seek(item.header_offset)
        header=source.read(30)
        if header[:4]!=b'PK\x03\x04':raise AssertionError('Invalid ZIP local header')
        filename,extra=struct.unpack_from('<HH',header,26)
        offset=item.header_offset+30+filename+extra
        if item.compress_type!=zipfile.ZIP_STORED:raise AssertionError('resources.arsc must be uncompressed')
        if offset%alignment:raise AssertionError(f'resources.arsc offset {offset} is not {alignment}-byte aligned')
        if struct.unpack_from('<H',header,8)[0]!=zipfile.ZIP_STORED:raise AssertionError('Local ZIP compression differs')
        return {'compression':'STORED','data_offset':offset,'alignment':alignment,'bytes':item.file_size}

def verify_apk(path):
    report={'outer':inspect(path.read_bytes())}
    with zipfile.ZipFile(path) as archive:
        for item in archive.infolist():
            if item.filename.startswith('assets/flyme-video/') and item.filename.endswith('.mp4'):
                if item.compress_type!=zipfile.ZIP_STORED:raise AssertionError('Video openFd requires uncompressed MP4: '+item.filename)
        if 'assets/ui/editor-ui.apk' in archive.namelist():
            report['embedded_ui']=inspect(archive.read('assets/ui/editor-ui.apk'))
        if 'assets/ui/settings-ui.apk' in archive.namelist():
            report['settings_ui']=inspect(archive.read('assets/ui/settings-ui.apk'))
    return report
