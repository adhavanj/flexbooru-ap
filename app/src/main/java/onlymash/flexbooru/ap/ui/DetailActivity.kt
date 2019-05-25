package onlymash.flexbooru.ap.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.*
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import kotlinx.android.synthetic.main.activity_detail.*
import kotlinx.android.synthetic.main.bottom_shortcut_bar.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import onlymash.flexbooru.ap.R
import onlymash.flexbooru.ap.common.QUERY_KEY
import onlymash.flexbooru.ap.common.Settings
import onlymash.flexbooru.ap.data.api.Api
import onlymash.flexbooru.ap.data.db.dao.DetailDao
import onlymash.flexbooru.ap.data.db.dao.PostDao
import onlymash.flexbooru.ap.data.model.Detail
import onlymash.flexbooru.ap.data.model.Post
import onlymash.flexbooru.ap.data.repository.detail.DetailRepositoryImpl
import onlymash.flexbooru.ap.data.repository.local.LocalRepositoryImpl
import onlymash.flexbooru.ap.extension.NetResult
import onlymash.flexbooru.ap.extension.getViewModel
import onlymash.flexbooru.ap.ui.base.BaseActivity
import onlymash.flexbooru.ap.ui.dialog.TagsDialog
import onlymash.flexbooru.ap.ui.diffcallback.PostDiffCallback
import onlymash.flexbooru.ap.ui.fragment.DetailFragment
import onlymash.flexbooru.ap.ui.viewmodel.DetailViewModel
import onlymash.flexbooru.ap.ui.viewmodel.LocalPostViewModel
import onlymash.flexbooru.ap.worker.DownloadWorker
import org.kodein.di.generic.instance

const val CURRENT_POSITION_KEY = "current_position"
const val FROM_WHERE_KEY = "from_where"
const val FROM_POSTS = 0
const val FROM_HISTORY = 1

class DetailActivity : BaseActivity() {

    companion object {
        private const val TAG = "DetailActivity"
        fun startDetailActivity(context: Context, fromWhere: Int, query: String = "", position: Int) {
            context.startActivity(Intent(context, DetailActivity::class.java).apply {
                putExtra(FROM_WHERE_KEY, fromWhere)
                putExtra(QUERY_KEY, query)
                putExtra(CURRENT_POSITION_KEY, position)
            })
        }
    }

    private val api by instance<Api>()
    private val detailDao by instance<DetailDao>()
    private val postDao by instance<PostDao>()

    private lateinit var localPostViewModel: LocalPostViewModel
    private lateinit var localDetailViewModel: DetailViewModel
    private lateinit var detailAdapter: DetailAdapter

    private var fromWhere = FROM_POSTS
    private var pos = 0
    private var query = ""
    private val posts: MutableList<Post> = mutableListOf()
    private val details: MutableList<Detail> = mutableListOf()
    private var currentPostId = 0
    private val allDetails: MutableList<Detail> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initWindow()
        setContentView(R.layout.activity_detail)
        intent?.apply {
            fromWhere = getIntExtra(FROM_WHERE_KEY, FROM_POSTS)
            query = getStringExtra(QUERY_KEY) ?: ""
            pos = getIntExtra(CURRENT_POSITION_KEY, 0)
        }
        initView()
        initViewModel()
    }
    private fun initView() {
        detailAdapter = DetailAdapter(supportFragmentManager, lifecycle)
        posts_pager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        posts_pager.adapter = detailAdapter
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        ViewCompat.setOnApplyWindowInsetsListener(posts_pager) { _, insets ->
            toolbar_container.minimumHeight = toolbar.height + insets.systemWindowInsetTop
            toolbar_container.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                0
            )
            space_nav_bar.minimumHeight = insets.systemWindowInsetBottom
            insets
        }
        posts_pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                pos = position
                when (fromWhere) {
                    FROM_POSTS -> currentPostId = posts[position].id
                    FROM_HISTORY -> currentPostId = details[position].id
                }
                toolbar.title = "Post $currentPostId"
                initVoteIcon()
            }
        })
        post_tags.setOnClickListener {
            TagsDialog.newInstance(currentPostId).show(supportFragmentManager, "tags")
        }
        post_info.setOnClickListener {

        }
        post_save.setOnClickListener {
            if (fromWhere == FROM_HISTORY && details.isNotEmpty()) {
                if (details.isEmpty()) return@setOnClickListener
                DownloadWorker.download(this, details[pos])
            } else if (fromWhere == FROM_POSTS && posts.isNotEmpty()) {
                lifecycleScope.launch {
                    val detail = withContext(Dispatchers.IO) {
                        detailDao.getDetailById(posts[pos].id)
                    }
                    if (detail != null) {
                        DownloadWorker.download(this@DetailActivity, detail)
                    }
                }
            }
        }
    }

    private fun initViewModel() {
        if (fromWhere == FROM_POSTS) {
            localPostViewModel = getViewModel(object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                    return LocalPostViewModel(LocalRepositoryImpl(postDao)) as T
                }
            })
            localPostViewModel.posts.observe(this, Observer {
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val oldItems = mutableListOf<Post>()
                        oldItems.addAll(posts)
                        posts.clear()
                        posts.addAll(it)
                        DiffUtil.calculateDiff(PostDiffCallback(oldItems, posts))
                    }
                    result.dispatchUpdatesTo(detailAdapter)
                    posts_pager.setCurrentItem(pos, false)
                }
                toolbar.title = "Post ${it[pos].id}"
                localPostViewModel.posts.removeObservers(this)
            })
            localPostViewModel.load(query)
        }

        localDetailViewModel = getViewModel(object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                return DetailViewModel(
                    repo = DetailRepositoryImpl(api = api, detailDao = detailDao),
                    scheme = Settings.scheme,
                    host = Settings.hostname
                ) as T
            }
        })
        localDetailViewModel.details.observe(this, Observer {
            allDetails.clear()
            allDetails.addAll(it)
            if (fromWhere == FROM_HISTORY && details.isEmpty()) {
                toolbar.title = "Post ${it[pos].id}"
                details.addAll(it)
                detailAdapter.notifyDataSetChanged()
                posts_pager.setCurrentItem(pos, false)
            }
            initVoteIcon()
        })
        localDetailViewModel.loadAll()
        localDetailViewModel.voteResult.observe(this, Observer {
            when (it) {
                is NetResult.Success -> {

                }
                is NetResult.Error -> {
                    Toast.makeText(this, it.errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        })
        post_vote.setOnClickListener {
            val token = Settings.userToken
            if (token.isEmpty()) {
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                val postId = currentPostId
                if (postId <= 0) return@setOnClickListener
                val detail = getCurrentDetail(postId) ?: return@setOnClickListener
                val vote = if (detail.starIt) 0 else 9
                localDetailViewModel.vote(vote, token, detail)
            }
        }
    }

    private fun getCurrentDetail(postId: Int): Detail? {
        var detail: Detail? = null
        allDetails.forEach {
            if (it.id == postId) {
                detail = it
                return@forEach
            }
        }
        return detail
    }

    private fun initVoteIcon() {
        if (isVoted(currentPostId)) {
            post_vote.setImageDrawable(
                ContextCompat.getDrawable(
                    this@DetailActivity,
                    R.drawable.ic_star_24dp
                )
            )
        } else {
            post_vote.setImageDrawable(
                ContextCompat.getDrawable(
                    this@DetailActivity,
                    R.drawable.ic_star_border_24dp
                )
            )
        }
    }

    private fun isVoted(postId: Int): Boolean {
        val index = allDetails.indexOfFirst { it.id == postId && it.starIt }
        return index > -1
    }

    private fun initWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        showBar()
    }

    internal fun setVisibility() {
        when (toolbar.visibility) {
            View.VISIBLE -> {
                hideBar()
                toolbar.visibility = View.GONE
                bottom_bar_container.visibility = View.GONE
                shadow.visibility = View.GONE
            }
            else -> {
                showBar()
                toolbar.visibility = View.VISIBLE
                bottom_bar_container.visibility = View.VISIBLE
                shadow.visibility = View.VISIBLE
            }
        }
    }

    private fun showBar() {
        val uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.decorView.systemUiVisibility = uiFlags
    }

    private fun hideBar() {
        val uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE
        window.decorView.systemUiVisibility = uiFlags
    }

    inner class DetailAdapter(
        fm: FragmentManager,
        lifecycle: Lifecycle
    ) : FragmentStateAdapter(fm, lifecycle) {

        override fun getItem(position: Int): Fragment =
            DetailFragment.newInstance(
                if (fromWhere == FROM_POSTS)
                    posts[position].id
                else
                    details[position].id
            )

        override fun getItemCount(): Int =
            if (fromWhere == FROM_POSTS)
                posts.size
            else
                details.size
    }
}
